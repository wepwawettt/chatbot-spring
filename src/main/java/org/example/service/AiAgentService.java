package org.example.service;

import org.example.dto.ActionType;
import org.example.dto.AiActionResponse;
import org.example.dto.AlarmCreateRequest;
import org.example.dto.AlarmResponse;
import org.example.dto.ChatResponse;
import org.example.dto.DeviceCreateRequest;
import org.example.dto.DeviceResponse;
import org.example.dto.GeminiFilters;
import org.example.dto.OperationType;
import org.example.service.AlarmService.DateRangeCount;
import org.example.service.AlarmService.GroupCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiAgentService {
    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final Pattern LAST_LIMIT_PATTERN = Pattern.compile("\\bson\\s+(\\d{1,3})\\b");
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("tr-TR"));
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"));
    private static final Set<String> DEVICE_STATUSES = Set.of("ACTIVE", "PASSIVE", "MAINTENANCE");
    private static final Set<String> DEVICE_TYPES = Set.of("CAMERA", "SENSOR", "GATEWAY", "UPS", "GENERATOR", "THERMOSTAT");
    private static final Set<String> ALARM_TYPES = Set.of("MOTION", "TEMPERATURE", "CONNECTION");
    private static final Set<String> ALARM_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");

    private final GeminiClient geminiClient;
    private final DeviceService deviceService;
    private final AlarmService alarmService;
    private final Map<String, PendingCommand> pendingCommands = new ConcurrentHashMap<>();

    public AiAgentService(GeminiClient geminiClient,
                          DeviceService deviceService,
                          AlarmService alarmService) {
        this.geminiClient = geminiClient;
        this.deviceService = deviceService;
        this.alarmService = alarmService;
    }

    public ChatResponse answer(String username, boolean admin, String message) {
        return answer(username, admin, null, message);
    }

    @Transactional
    public ChatResponse answer(String username, boolean admin, String conversationId, String message) {
        String safeMessage = message == null ? "" : message;
        String sessionKey = sessionKey(username, conversationId);
        try {
            Optional<ChatResponse> pendingResponse = answerPendingCommand(sessionKey, username, admin, safeMessage);
            if (pendingResponse.isPresent()) {
                return pendingResponse.get();
            }

            Optional<ChatResponse> directResponse = directResponse(safeMessage);
            if (directResponse.isPresent()) {
                return directResponse.get();
            }

            Optional<AiActionResponse> geminiAction = geminiClient.extractAction(safeMessage);
            AiActionResponse action = geminiAction.orElse(null);
            if (action == null || isUnknown(action) || commandNeedsTargetFallback(action)) {
                AiActionResponse fallback = fallbackAction(safeMessage);
                if (!isUnknown(fallback) || action == null) {
                    action = fallback;
                }
            }

            ChatResponse response = route(username, admin, sessionKey, action);
            if (!response.success() && isUnknown(action)) {
                return unknownWithHint();
            }
            return response;
        } catch (RuntimeException exception) {
            log.warn("AI agent routing failed.", exception);
            return new ChatResponse("Istegini guvenli sekilde isleyemedim.", false);
        }
    }

    private ChatResponse route(String username, boolean admin, String sessionKey, AiActionResponse actionResponse) {
        ActionType action = actionResponse.action() == null ? ActionType.UNKNOWN : actionResponse.action();
        OperationType operation = actionResponse.operation() == null ? OperationType.UNKNOWN : actionResponse.operation();
        GeminiFilters filters = actionResponse.filters() == null ? new GeminiFilters() : actionResponse.filters();

        return switch (action) {
            case DEVICE_QUERY -> routeDeviceQuery(username, admin, operation, filters, actionResponse.limit());
            case ALARM_QUERY -> routeAlarmQuery(username, admin, operation, actionResponse, filters);
            case DEVICE_COMMAND, ALARM_COMMAND -> routeCommand(username, admin, sessionKey, action, operation, actionResponse, filters);
            case CALCULATION -> routeCalculation(username, admin, operation, actionResponse, filters);
            case GENERAL_QUESTION -> generalQuestion();
            case UNKNOWN -> unknown();
        };
    }

    private ChatResponse routeDeviceQuery(String username,
                                          boolean admin,
                                          OperationType operation,
                                          GeminiFilters filters,
                                          Integer requestedLimit) {
        return switch (operation) {
            case LIST -> listDevices(username, admin, filters, requestedLimit);
            case COUNT -> countDevices(username, admin, filters);
            case DETAIL -> deviceDetail(username, admin, filters);
            default -> unknown();
        };
    }

    private ChatResponse routeAlarmQuery(String username,
                                         boolean admin,
                                         OperationType operation,
                                         AiActionResponse actionResponse,
                                         GeminiFilters filters) {
        return switch (operation) {
            case LIST -> listAlarms(username, admin, filters, actionResponse.limit());
            case COUNT -> countAlarms(username, admin, filters);
            case GROUP_BY -> groupAlarms(username, admin, actionResponse.groupBy(), filters);
            case TOP_N -> topAlarmGroups(username, admin, actionResponse.groupBy(), actionResponse.metric(), filters, actionResponse.limit());
            default -> unknown();
        };
    }

    private ChatResponse routeCalculation(String username,
                                          boolean admin,
                                          OperationType operation,
                                          AiActionResponse actionResponse,
                                          GeminiFilters filters) {
        if (!isAlarmCountMetric(actionResponse)) {
            // TODO: device_count, resolved duration, severity-based metrics gibi metrikler eklenebilir.
            return unknown();
        }

        return switch (operation) {
            case AVERAGE -> averageAlarmCounts(username, admin, filters);
            case SUM -> sumAlarmCounts(username, admin, filters);
            case MIN -> minAlarmCount(username, admin, filters);
            case MAX -> maxAlarmCount(username, admin, filters);
            case COMPARE -> compareAlarmCounts(username, admin, filters);
            default -> unknown();
        };
    }

    private ChatResponse routeCommand(String username,
                                      boolean admin,
                                      String sessionKey,
                                      ActionType action,
                                      OperationType operation,
                                      AiActionResponse actionResponse,
                                      GeminiFilters filters) {
        if (!admin) {
            return new ChatResponse("Bu islem icin admin yetkisi gerekiyor.", false);
        }

        return switch (action) {
            case DEVICE_COMMAND -> routeDeviceCommand(username, admin, sessionKey, operation, actionResponse, filters);
            case ALARM_COMMAND -> routeAlarmCommand(username, admin, sessionKey, operation, actionResponse, filters);
            default -> unknown();
        };
    }

    private ChatResponse routeDeviceCommand(String username,
                                            boolean admin,
                                            String sessionKey,
                                            OperationType operation,
                                            AiActionResponse actionResponse,
                                            GeminiFilters filters) {
        return switch (operation) {
            case UPDATE_STATUS -> requestDeviceStatusUpdate(username, admin, sessionKey, actionResponse, filters);
            case CREATE -> requestDeviceCreate(sessionKey, actionResponse, filters);
            default -> unknown();
        };
    }

    private ChatResponse routeAlarmCommand(String username,
                                           boolean admin,
                                           String sessionKey,
                                           OperationType operation,
                                           AiActionResponse actionResponse,
                                           GeminiFilters filters) {
        String requestedStatus = allow(normalize(firstNonBlank(actionResponse.status(), filters.getStatus())), Set.of("RESOLVED"));
        if (operation == OperationType.UPDATE_STATUS && "RESOLVED".equals(requestedStatus)) {
            return requestAlarmResolve(sessionKey, actionResponse);
        }

        return switch (operation) {
            case RESOLVE -> requestAlarmResolve(sessionKey, actionResponse);
            case CREATE -> requestAlarmCreate(username, admin, sessionKey, actionResponse, filters);
            default -> unknown();
        };
    }

    private ChatResponse requestDeviceStatusUpdate(String username,
                                                   boolean admin,
                                                   String sessionKey,
                                                   AiActionResponse actionResponse,
                                                   GeminiFilters filters) {
        String requestedStatus = allow(normalize(firstNonBlank(actionResponse.status(), filters.getStatus())), DEVICE_STATUSES);
        if (requestedStatus == null) {
            return new ChatResponse("Cihaz durumu icin ACTIVE, PASSIVE veya MAINTENANCE degeri gerekiyor.", false);
        }

        DeviceResolution resolution = resolveDevice(username, admin, actionResponse, filters);
        if (resolution.ambiguous()) {
            return new ChatResponse("Birden fazla cihaz eslesti: " + summarizeDevices(resolution.matches())
                    + ". Lutfen komutu cihaz id ile yaz.", false);
        }
        if (resolution.isNotFound()) {
            return new ChatResponse("Islem icin tek bir cihaz belirleyemedim. Cihaz id veya adini yaz.", false);
        }

        DeviceResponse device = resolution.device();
        PendingCommand pending = PendingCommand.deviceStatus(
                device.getId(),
                device.getName(),
                requestedStatus,
                "#" + device.getId() + " " + device.getName() + " cihazinin durumunu " + requestedStatus
                        + " yapayim mi? Onay icin evet, iptal icin hayir yaz."
        );
        pendingCommands.put(sessionKey, pending);
        return new ChatResponse(pending.confirmationText(), false);
    }

    private ChatResponse requestDeviceCreate(String sessionKey, AiActionResponse actionResponse, GeminiFilters filters) {
        String deviceName = firstNonBlank(actionResponse.entityName(), filters.getNameContains());
        String deviceType = allow(normalize(firstNonBlank(actionResponse.deviceType(), filters.getDeviceType())), DEVICE_TYPES);
        String status = allow(normalize(firstNonBlank(actionResponse.status(), filters.getStatus())), DEVICE_STATUSES);

        if (isBlank(deviceName) || deviceType == null) {
            return new ChatResponse("Cihaz olusturmak icin cihaz adi ve cihaz tipi gerekiyor.", false);
        }

        String finalStatus = status == null ? "ACTIVE" : status;
        PendingCommand pending = PendingCommand.createDevice(
                deviceName.trim(),
                deviceType,
                finalStatus,
                deviceName.trim() + " adli " + deviceType + " cihazini " + finalStatus
                        + " durumuyla olusturayim mi? Onay icin evet, iptal icin hayir yaz."
        );
        pendingCommands.put(sessionKey, pending);
        return new ChatResponse(pending.confirmationText(), false);
    }

    private ChatResponse requestAlarmResolve(String sessionKey, AiActionResponse actionResponse) {
        Long alarmId = actionResponse.entityId();
        if (alarmId == null) {
            return new ChatResponse("Alarm cozmek icin alarm id gerekiyor.", false);
        }

        PendingCommand pending = PendingCommand.resolveAlarm(
                alarmId,
                "#" + alarmId + " numarali alarmi cozuldu olarak isaretleyeyim mi? Onay icin evet, iptal icin hayir yaz."
        );
        pendingCommands.put(sessionKey, pending);
        return new ChatResponse(pending.confirmationText(), false);
    }

    private ChatResponse requestAlarmCreate(String username,
                                            boolean admin,
                                            String sessionKey,
                                            AiActionResponse actionResponse,
                                            GeminiFilters filters) {
        DeviceResolution resolution = resolveDevice(username, admin, actionResponse, filters);
        if (resolution.ambiguous()) {
            return new ChatResponse("Birden fazla cihaz eslesti: " + summarizeDevices(resolution.matches())
                    + ". Alarm olusturmak icin cihaz id ile yaz.", false);
        }
        if (resolution.isNotFound()) {
            return new ChatResponse("Alarm olusturmak icin tek bir cihaz id veya adi gerekiyor.", false);
        }

        DeviceResponse device = resolution.device();
        String alarmType = allow(normalize(actionResponse.alarmType()), ALARM_TYPES);
        String severity = allow(normalize(actionResponse.severity()), ALARM_SEVERITIES);
        if (alarmType == null) {
            return new ChatResponse("Alarm olusturmak icin alarm tipi gerekiyor: MOTION, TEMPERATURE veya CONNECTION.", false);
        }

        String finalSeverity = severity == null ? "MEDIUM" : severity;
        String description = firstNonBlank(actionResponse.description(), "Chatbot tarafindan olusturuldu.");
        PendingCommand pending = PendingCommand.createAlarm(
                device.getId(),
                device.getName(),
                alarmType,
                finalSeverity,
                description,
                "#" + device.getId() + " " + device.getName() + " icin " + finalSeverity + " onemde " + alarmType
                        + " alarmi olusturayim mi? Onay icin evet, iptal icin hayir yaz."
        );
        pendingCommands.put(sessionKey, pending);
        return new ChatResponse(pending.confirmationText(), false);
    }

    private ChatResponse listDevices(String username, boolean admin, GeminiFilters filters, Integer requestedLimit) {
        List<DeviceResponse> devices = deviceService.listDevices(
                username,
                admin,
                filters.getStatus(),
                filters.getDeviceType(),
                filters.getNameContains(),
                limit(requestedLimit)
        );

        if (devices.isEmpty()) {
            return new ChatResponse("Cihaz bulunamadi.", true);
        }

        return new ChatResponse(devices.size() + " cihaz bulundu: " + summarizeDevices(devices), true);
    }

    private ChatResponse countDevices(String username, boolean admin, GeminiFilters filters) {
        long count = deviceService.countDevices(
                username,
                admin,
                filters.getStatus(),
                filters.getDeviceType(),
                filters.getNameContains()
        );
        return new ChatResponse("Cihaz sayisi: " + count + ".", true);
    }

    private ChatResponse deviceDetail(String username, boolean admin, GeminiFilters filters) {
        String nameContains = filters.getNameContains();
        if (nameContains == null || nameContains.isBlank()) {
            return unknown();
        }

        return deviceService.getDeviceDetail(username, admin, nameContains)
                .map(device -> new ChatResponse(
                        "Cihaz detayi: " + device.getName()
                                + ", durum: " + device.getStatus()
                                + ", tip: " + device.getDeviceType()
                                + ", konum: " + valueOrDash(device.getLocation()) + ".",
                        true
                ))
                .orElseGet(() -> new ChatResponse("Bu isimle gorulebilir cihaz bulunamadi.", true));
    }

    private ChatResponse listAlarms(String username, boolean admin, GeminiFilters filters, Integer requestedLimit) {
        List<AlarmResponse> alarms = alarmService.listAlarms(
                username,
                admin,
                filters.getStatus(),
                filters.getDateRange(),
                filters.getStartDate(),
                filters.getEndDate(),
                limit(requestedLimit)
        );

        if (alarms.isEmpty()) {
            return new ChatResponse(datePrefix(filters) + "alarm bulunamadi.", true);
        }

        return new ChatResponse(alarms.size() + " alarm bulundu: " + summarizeAlarms(alarms), true);
    }

    private ChatResponse countAlarms(String username, boolean admin, GeminiFilters filters) {
        long count = alarmService.countAlarms(
                username,
                admin,
                filters.getStatus(),
                filters.getDateRange(),
                filters.getStartDate(),
                filters.getEndDate()
        );
        return new ChatResponse(datePrefix(filters) + "alarm sayisi: " + count + ".", true);
    }

    private ChatResponse groupAlarms(String username, boolean admin, String groupBy, GeminiFilters filters) {
        List<GroupCount> groups = alarmService.groupAlarmsBy(
                username,
                admin,
                defaultIfBlank(groupBy, "device"),
                filters.getStatus(),
                filters.getDateRange(),
                filters.getStartDate(),
                filters.getEndDate()
        );

        if (groups.isEmpty()) {
            return new ChatResponse("Gruplanacak alarm bulunamadi.", true);
        }

        return new ChatResponse("Alarm dagilimi: " + summarizeGroups(groups), true);
    }

    private ChatResponse topAlarmGroups(String username,
                                        boolean admin,
                                        String groupBy,
                                        String metric,
                                        GeminiFilters filters,
                                        Integer requestedLimit) {
        List<GroupCount> groups = alarmService.getTopAlarmGroups(
                username,
                admin,
                defaultIfBlank(groupBy, "device"),
                metric,
                filters.getStatus(),
                filters.getDateRange(),
                filters.getStartDate(),
                filters.getEndDate(),
                limit(requestedLimit)
        );

        if (groups.isEmpty()) {
            return new ChatResponse("Alarm grubu bulunamadi.", true);
        }

        GroupCount first = groups.getFirst();
        if (groups.size() == 1) {
            return new ChatResponse("En cok alarm veren grup: " + first.key() + ", alarm sayisi: " + first.count() + ".", true);
        }
        return new ChatResponse("En cok alarm veren gruplar: " + summarizeGroups(groups), true);
    }

    private ChatResponse averageAlarmCounts(String username, boolean admin, GeminiFilters filters) {
        List<DateRangeCount> counts = alarmCountsForRequestedRanges(username, admin, filters);
        OptionalDouble average = counts.stream().mapToLong(DateRangeCount::count).average();
        if (average.isEmpty()) {
            return unknown();
        }
        return new ChatResponse("Alarm sayisi ortalamasi: " + formatNumber(average.getAsDouble()) + ".", true);
    }

    private ChatResponse sumAlarmCounts(String username, boolean admin, GeminiFilters filters) {
        long sum = alarmCountsForRequestedRanges(username, admin, filters)
                .stream()
                .mapToLong(DateRangeCount::count)
                .sum();
        return new ChatResponse("Alarm sayisi toplami: " + sum + ".", true);
    }

    private ChatResponse minAlarmCount(String username, boolean admin, GeminiFilters filters) {
        return alarmCountsForRequestedRanges(username, admin, filters)
                .stream()
                .min(Comparator.comparingLong(DateRangeCount::count))
                .map(value -> new ChatResponse("En dusuk alarm sayisi: " + value.count() + " (" + value.dateRange() + ").", true))
                .orElseGet(this::unknown);
    }

    private ChatResponse maxAlarmCount(String username, boolean admin, GeminiFilters filters) {
        return alarmCountsForRequestedRanges(username, admin, filters)
                .stream()
                .max(Comparator.comparingLong(DateRangeCount::count))
                .map(value -> new ChatResponse("En yuksek alarm sayisi: " + value.count() + " (" + value.dateRange() + ").", true))
                .orElseGet(this::unknown);
    }

    private ChatResponse compareAlarmCounts(String username, boolean admin, GeminiFilters filters) {
        List<DateRangeCount> counts = alarmCountsForRequestedRanges(username, admin, filters);
        if (counts.size() < 2) {
            return unknown();
        }

        DateRangeCount first = counts.get(0);
        DateRangeCount second = counts.get(1);
        long difference = first.count() - second.count();
        return new ChatResponse(
                first.dateRange() + " alarm sayisi " + first.count()
                        + ", " + second.dateRange() + " alarm sayisi " + second.count()
                        + ". Fark: " + difference + ".",
                true
        );
    }

    private List<DateRangeCount> alarmCountsForRequestedRanges(String username, boolean admin, GeminiFilters filters) {
        List<String> dateRanges = Stream.concat(
                        filters.getDateRanges() == null ? Stream.empty() : filters.getDateRanges().stream(),
                        filters.getDateRange() == null ? Stream.empty() : Stream.of(filters.getDateRange())
                )
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        return alarmService.countAlarmsForDateRanges(username, admin, filters.getStatus(), dateRanges);
    }

    private boolean isAlarmCountMetric(AiActionResponse actionResponse) {
        String target = normalize(actionResponse.target());
        String metric = normalize(actionResponse.metric());
        return "ALARM_COUNT".equals(target)
                || "ALARMS".equals(target)
                || "ALARM_COUNT".equals(metric)
                || "COUNT".equals(metric);
    }

    private Optional<ChatResponse> answerPendingCommand(String sessionKey, String username, boolean admin, String message) {
        PendingCommand pending = pendingCommands.get(sessionKey);
        if (pending == null) {
            return Optional.empty();
        }

        String text = normalizeText(message);
        if (isApproval(text)) {
            pendingCommands.remove(sessionKey);
            return Optional.of(executePendingCommand(username, admin, pending));
        }
        if (isRejection(text)) {
            pendingCommands.remove(sessionKey);
            return Optional.of(new ChatResponse("Islem iptal edildi.", true));
        }

        return Optional.of(new ChatResponse(
                "Bekleyen bir islem var: " + pending.confirmationText() + " Devam etmek icin evet veya hayir yaz.",
                false
        ));
    }

    private ChatResponse executePendingCommand(String username, boolean admin, PendingCommand pending) {
        if (!admin) {
            return new ChatResponse("Bu islem icin admin yetkisi gerekiyor.", false);
        }

        try {
            return switch (pending.type()) {
                case UPDATE_DEVICE_STATUS -> {
                    DeviceResponse device = deviceService.updateDeviceStatus(pending.deviceId(), pending.status());
                    yield new ChatResponse(device.getName() + " cihazinin durumu " + device.getStatus() + " yapildi.", true);
                }
                case CREATE_DEVICE -> {
                    DeviceResponse device = deviceService.createDevice(new DeviceCreateRequest(
                            pending.deviceName(),
                            pending.deviceType(),
                            null
                    ));
                    if (!"ACTIVE".equals(pending.status())) {
                        device = deviceService.updateDeviceStatus(device.getId(), pending.status());
                    }
                    yield new ChatResponse(device.getName() + " cihazi olusturuldu. Durum: " + device.getStatus() + ".", true);
                }
                case CREATE_ALARM -> {
                    AlarmResponse alarm = alarmService.createAlarm(new AlarmCreateRequest(
                            pending.deviceId(),
                            pending.alarmType(),
                            pending.severity(),
                            pending.description(),
                            null
                    ));
                    yield new ChatResponse("#" + alarm.getId() + " alarmi olusturuldu: "
                            + alarm.getDeviceName() + " - " + alarm.getAlarmType() + " (" + alarm.getSeverity() + ").", true);
                }
                case RESOLVE_ALARM -> {
                    AlarmResponse alarm = alarmService.resolveAlarm(pending.alarmId());
                    yield new ChatResponse("#" + alarm.getId() + " alarmi cozuldu olarak isaretlendi.", true);
                }
            };
        } catch (RuntimeException exception) {
            log.warn("Pending command execution failed for user {}.", username, exception);
            return new ChatResponse("Islem uygulanamadi. Kayit bulunamamis veya dogrulama basarisiz olabilir.", false);
        }
    }

    private AiActionResponse fallbackAction(String message) {
        String text = normalizeText(message);
        if (text.isBlank() || hasUnsafeTerms(text)) {
            return AiActionResponse.unknown();
        }
        if (isGeneralHelpQuestion(text)) {
            return new AiActionResponse(
                    ActionType.GENERAL_QUESTION,
                    OperationType.UNKNOWN,
                    null,
                    null,
                    null,
                    new GeminiFilters(),
                    null
            );
        }
        if (isCommandRequest(text)) {
            AiActionResponse command = fallbackCommandAction(text);
            if (!isUnknown(command)) {
                return command;
            }
        }
        if (isAlarmQuery(text)) {
            return fallbackAlarmAction(text);
        }
        if (isDeviceQuery(text)) {
            return fallbackDeviceAction(text);
        }
        return AiActionResponse.unknown();
    }

    private AiActionResponse fallbackCommandAction(String text) {
        if (isAlarmQuery(text) && containsAny(text, "coz", "cozuldu", "cozulmus yap", "kapat")) {
            Long alarmId = extractFirstNumber(text);
            if (alarmId == null) {
                return AiActionResponse.unknown();
            }
            return new AiActionResponse(
                    ActionType.ALARM_COMMAND,
                    OperationType.RESOLVE,
                    "alarm",
                    null,
                    null,
                    new GeminiFilters(),
                    null,
                    alarmId,
                    null,
                    "RESOLVED",
                    null,
                    null,
                    null,
                    null
            );
        }

        String status = detectDeviceStatus(text);
        if (isDeviceQuery(text) && status != null && containsAny(text, "yap", "ayarla", "guncelle", "degistir")) {
            GeminiFilters filters = new GeminiFilters();
            filters.setStatus(status);
            Long deviceId = extractFirstNumber(text);
            String entityName = deviceId == null ? extractDeviceCommandName(text, status) : null;
            return new AiActionResponse(
                    ActionType.DEVICE_COMMAND,
                    OperationType.UPDATE_STATUS,
                    "device",
                    null,
                    null,
                    filters,
                    null,
                    deviceId,
                    entityName,
                    status,
                    null,
                    null,
                    null,
                    null
            );
        }

        return AiActionResponse.unknown();
    }

    private AiActionResponse fallbackAlarmAction(String text) {
        GeminiFilters filters = new GeminiFilters();
        filters.setDateRange(detectDateRange(text));
        filters.setStatus(detectAlarmStatus(text));

        OperationType operation = detectAlarmOperation(text);
        return new AiActionResponse(
                ActionType.ALARM_QUERY,
                operation,
                "alarms",
                detectAlarmGroupBy(text, operation),
                operation == OperationType.COUNT || operation == OperationType.TOP_N ? "alarm_count" : null,
                filters,
                operation == OperationType.COUNT ? null : detectLimit(text, operation == OperationType.TOP_N ? 1 : DEFAULT_LIMIT)
        );
    }

    private AiActionResponse fallbackDeviceAction(String text) {
        GeminiFilters filters = new GeminiFilters();
        filters.setStatus(detectDeviceStatus(text));
        filters.setDeviceType(detectDeviceType(text));
        filters.setNameContains(detectDeviceName(text));

        OperationType operation = detectDeviceOperation(text);
        return new AiActionResponse(
                ActionType.DEVICE_QUERY,
                operation,
                "devices",
                null,
                operation == OperationType.COUNT ? "device_count" : null,
                filters,
                operation == OperationType.COUNT ? null : detectLimit(text, DEFAULT_LIMIT)
        );
    }

    private OperationType detectAlarmOperation(String text) {
        if (containsAny(text, "en cok", "top", "ilk siradaki")) {
            return OperationType.TOP_N;
        }
        if (containsAny(
                text,
                "grupla", "gruplandir", "dagilim", "dagilimi",
                "cihaza gore", "hangi cihaz", "cihazlarda", "cihazlarda alarm",
                "duruma gore", "gune gore"
        )) {
            return OperationType.GROUP_BY;
        }
        if (containsAny(text, "kac", "sayisi", "sayi", "adet", "toplam")) {
            return OperationType.COUNT;
        }
        return OperationType.LIST;
    }

    private OperationType detectDeviceOperation(String text) {
        if (containsAny(text, "detay", "detayi", "bilgi", "bilgisi")) {
            return OperationType.DETAIL;
        }
        if (containsAny(text, "kac", "sayisi", "sayi", "adet", "toplam")) {
            return OperationType.COUNT;
        }
        return OperationType.LIST;
    }

    private String detectAlarmGroupBy(String text, OperationType operation) {
        if (operation != OperationType.GROUP_BY && operation != OperationType.TOP_N) {
            return null;
        }
        if (containsAny(text, "cihaz", "cihaza", "cihazda", "cihazlar", "cihazlarda", "hangi cihaz")) {
            return "device";
        }
        if (containsAny(text, "cihaz tipi", "device type", "type", "tip", "turu")) {
            return "device_type";
        }
        if (containsAny(text, "durum", "status", "cozulmus", "cozulmemis")) {
            return "status";
        }
        if (containsAny(text, "gun", "tarih", "date")) {
            return "day";
        }
        return operation == OperationType.TOP_N ? "device" : null;
    }

    private String detectDateRange(String text) {
        if (containsAny(text, "dun", "dunku")) {
            return "YESTERDAY";
        }
        if (containsAny(text, "bugun", "bugunku")) {
            return "TODAY";
        }
        if (containsAny(text, "son 24", "son yirmi dort")) {
            return "LAST_24_HOURS";
        }
        if (containsAny(text, "son 7", "son yedi", "gecen hafta", "son hafta")) {
            return "LAST_7_DAYS";
        }
        return null;
    }

    private String detectAlarmStatus(String text) {
        if (containsAny(text, "cozulmemis", "acik", "aktif alarm", "unresolved")) {
            return "UNRESOLVED";
        }
        if (containsAny(text, "cozulmus", "kapali", "resolved")) {
            return "RESOLVED";
        }
        return null;
    }

    private String detectDeviceStatus(String text) {
        if (containsAny(text, "aktif", "active", "calisan")) {
            return "ACTIVE";
        }
        if (containsAny(text, "pasif", "passive")) {
            return "PASSIVE";
        }
        if (containsAny(text, "bakim", "maintenance")) {
            return "MAINTENANCE";
        }
        return null;
    }

    private String detectDeviceType(String text) {
        if (containsAny(text, "kamera", "camera")) {
            return "CAMERA";
        }
        if (containsAny(text, "sensor", "sensorde")) {
            return "SENSOR";
        }
        if (containsAny(text, "gateway")) {
            return "GATEWAY";
        }
        if (containsAny(text, "ups")) {
            return "UPS";
        }
        if (containsAny(text, "jenerator", "generator")) {
            return "GENERATOR";
        }
        return null;
    }

    private String detectDeviceName(String text) {
        Matcher matcher = Pattern.compile("\\b(?:adi|ismi|isim)\\s+([a-z0-9_-]{2,40})\\b").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer detectLimit(String text, int fallback) {
        Matcher matcher = LAST_LIMIT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean isAlarmQuery(String text) {
        return containsAny(text, "alarm", "alarmlar", "alarmlari", "ariza", "uyari");
    }

    private boolean isDeviceQuery(String text) {
        return containsAny(text, "cihaz", "cihazlar", "cihazlari", "kamera", "sensor", "gateway", "ups", "jenerator");
    }

    private boolean isGeneralHelpQuestion(String text) {
        return containsAny(
                text,
                "ne yapabilirsin",
                "ne yapabiliyorsun",
                "neler yapabilirsin",
                "neler yapabiliyorsun",
                "ne yaparsin",
                "yardim",
                "help",
                "neleri sorabilirim",
                "hangi sorulari sorabilirim",
                "nasil yardim edebilirsin"
        );
    }

    private Optional<ChatResponse> directResponse(String message) {
        String text = normalizeText(message);
        if (text.isBlank()) {
            return Optional.of(new ChatResponse("Bir cihaz veya alarm sorusu yazabilirsin.", false));
        }
        if (hasUnsafeTerms(text)) {
            return Optional.of(new ChatResponse("Bu istegi guvenlik nedeniyle yerine getiremem.", false));
        }
        if (isGreeting(text)) {
            return Optional.of(greeting());
        }
        if (isWellbeingQuestion(text)) {
            return Optional.of(new ChatResponse(
                    "Calisiyorum. Cihazlar ve alarmlar hakkinda soru sorabilirsin.",
                    true
            ));
        }
        if (isTimeQuestion(text)) {
            return Optional.of(timeAnswer());
        }
        if (isDateQuestion(text)) {
            return Optional.of(dateAnswer());
        }
        if (isThanks(text)) {
            return Optional.of(new ChatResponse("Rica ederim. Cihazlar veya alarmlar hakkinda soru sorabilirsin.", true));
        }
        if (isAlarmDefinitionQuestion(text)) {
            return Optional.of(new ChatResponse(
                    "Alarm, bir cihazda olusan uyari veya ariza kaydidir. Alarm listesi, alarm sayisi, son alarmlar veya en cok alarm veren cihazlari sorabilirsin.",
                    true
            ));
        }
        if (isDeviceDefinitionQuestion(text)) {
            return Optional.of(new ChatResponse(
                    "Cihaz, sistemde takip edilen kamera, sensor, gateway, UPS veya benzeri ekipmandir. Aktif cihazlari, bakimdaki cihazlari veya cihaz detaylarini sorabilirsin.",
                    true
            ));
        }
        if (isBareDomainKeyword(text) || isGeneralHelpQuestion(text)) {
            return Optional.of(generalQuestion());
        }
        return Optional.empty();
    }

    private DeviceResolution resolveDevice(String username,
                                           boolean admin,
                                           AiActionResponse actionResponse,
                                           GeminiFilters filters) {
        if (actionResponse.entityId() != null) {
            return deviceService.getDeviceDetail(username, admin, String.valueOf(actionResponse.entityId()))
                    .map(DeviceResolution::found)
                    .orElseGet(DeviceResolution::notFound);
        }

        String identifier = firstNonBlank(actionResponse.entityName(), filters.getNameContains());
        if (isBlank(identifier)) {
            return DeviceResolution.notFound();
        }

        List<DeviceResponse> ambiguousMatches = List.of();
        for (String candidate : deviceIdentifierCandidates(identifier)) {
            List<DeviceResponse> matches = deviceService.listDevices(username, admin, null, null, candidate, MAX_LIMIT);
            if (matches.size() == 1) {
                return DeviceResolution.found(matches.getFirst());
            }
            if (matches.size() > 1 && ambiguousMatches.isEmpty()) {
                ambiguousMatches = matches;
            }
        }

        return ambiguousMatches.isEmpty()
                ? DeviceResolution.notFound()
                : DeviceResolution.ambiguous(ambiguousMatches);
    }

    private boolean isCommandRequest(String text) {
        return containsAny(text, "yap", "ayarla", "guncelle", "degistir", "olustur", "ekle", "coz", "cozuldu", "kapat");
    }

    private boolean isApproval(String text) {
        return matchesAny(text, "evet", "onayla", "tamam", "olur", "yap", "uygula", "yes");
    }

    private boolean isRejection(String text) {
        return matchesAny(text, "hayir", "hayir iptal", "iptal", "vazgec", "yok", "no");
    }

    private Long extractFirstNumber(String text) {
        Matcher matcher = Pattern.compile("\\b(\\d+)\\b").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractDeviceCommandName(String text, String status) {
        String cleaned = stripTurkishObjectSuffix(stripDeviceCommandWords(text, status));
        return cleaned.isBlank() ? null : cleaned;
    }

    private List<String> deviceIdentifierCandidates(String identifier) {
        String normalized = normalizeText(identifier);
        String commandCleaned = stripDeviceCommandWords(normalized, null);
        String suffixCleaned = stripTurkishObjectSuffix(commandCleaned);

        return Stream.of(identifier, normalized, commandCleaned, suffixCleaned)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String stripDeviceCommandWords(String text, String status) {
        String cleaned = normalizeText(text);
        if (status != null) {
            cleaned = cleaned.replace(status.toLowerCase(Locale.ROOT), " ");
        }
        return cleaned
                .replace("aktif", " ")
                .replace("active", " ")
                .replace("pasif", " ")
                .replace("passive", " ")
                .replace("bakim", " ")
                .replace("maintenance", " ")
                .replaceAll("\\b(cihazi|cihaz|durumunu|durumu|hale|al|alarak|eder|misin|miyim|lutfen|yapar|yap|ayarla|guncelle|degistir)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripTurkishObjectSuffix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Stream.of(value.split("\\s+"))
                .map(this::stripTurkishObjectSuffixFromToken)
                .filter(token -> !token.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    private String stripTurkishObjectSuffixFromToken(String token) {
        if (token.length() <= 4) {
            return token;
        }
        if (token.endsWith("unu") || token.endsWith("ini")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("nu") || token.endsWith("ni") || token.endsWith("yi")) {
            return token.substring(0, token.length() - 2);
        }
        return token;
    }

    private String allow(String value, Set<String> allowedValues) {
        return value != null && allowedValues.contains(value) ? value : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isGreeting(String text) {
        return matchesAny(text, "selam", "merhaba", "hello", "hi", "hey", "iyi gunler", "iyi aksamlar");
    }

    private boolean isWellbeingQuestion(String text) {
        return matchesAny(text, "nasilsin", "naber", "ne haber", "nasil gidiyor", "iyi misin", "keyfin nasil");
    }

    private boolean isTimeQuestion(String text) {
        return matchesAny(text, "saat kac", "su an saat kac", "simdi saat kac", "saat kac oldu", "kac saat");
    }

    private boolean isDateQuestion(String text) {
        return matchesAny(text, "tarih ne", "bugun tarih ne", "bugunun tarihi ne", "hangi gundayiz", "bugun hangi gun");
    }

    private boolean isThanks(String text) {
        return matchesAny(text, "tesekkurler", "tesekkur ederim", "sag ol", "sagol", "eyvallah");
    }

    private boolean isAlarmDefinitionQuestion(String text) {
        return matchesAny(text, "alarm ne", "alarm nedir", "alarm ne demek", "alarm ne ise yarar");
    }

    private boolean isDeviceDefinitionQuestion(String text) {
        return matchesAny(text, "cihaz ne", "cihaz nedir", "cihaz ne demek");
    }

    private boolean isBareDomainKeyword(String text) {
        return matchesAny(text, "alarm", "alarmlar", "cihaz", "cihazlar");
    }

    private boolean hasUnsafeTerms(String text) {
        return containsAny(
                text,
                "jdbc", "connection string", "sifre", "password", "secret", "token", "api key",
                "sql", "select ", "insert ", "update ", "delete from", "drop ", "truncate ",
                "veritabani sifresi", "database password", "sil"
        );
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(String text, String... values) {
        for (String value : values) {
            if (text.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnknown(AiActionResponse actionResponse) {
        if (actionResponse == null) {
            return true;
        }
        ActionType action = actionResponse.action() == null ? ActionType.UNKNOWN : actionResponse.action();
        OperationType operation = actionResponse.operation() == null ? OperationType.UNKNOWN : actionResponse.operation();
        return action == ActionType.UNKNOWN || (action != ActionType.GENERAL_QUESTION && operation == OperationType.UNKNOWN);
    }

    private boolean commandNeedsTargetFallback(AiActionResponse actionResponse) {
        ActionType action = actionResponse.action() == null ? ActionType.UNKNOWN : actionResponse.action();
        OperationType operation = actionResponse.operation() == null ? OperationType.UNKNOWN : actionResponse.operation();
        GeminiFilters filters = actionResponse.filters() == null ? new GeminiFilters() : actionResponse.filters();

        if (action == ActionType.DEVICE_COMMAND && operation == OperationType.UPDATE_STATUS) {
            return actionResponse.entityId() == null
                    && isBlank(actionResponse.entityName())
                    && isBlank(filters.getNameContains());
        }
        if (action == ActionType.ALARM_COMMAND && (operation == OperationType.RESOLVE || operation == OperationType.UPDATE_STATUS)) {
            return actionResponse.entityId() == null;
        }
        return false;
    }

    private ChatResponse generalQuestion() {
        return new ChatResponse(
                "Cihazlari ve alarmlari listeleyebilir, sayabilir, gruplayabilir ve alarm sayilari uzerinde basit hesaplamalar yapabilirim. Ornek: son 10 alarmi listele, aktif cihazlari getir, hangi cihazlarda alarm var.",
                true
        );
    }

    private ChatResponse greeting() {
        return new ChatResponse(
                "Merhaba. Cihazlar ve alarmlar hakkinda soru sorabilirsin. Ornek: son 10 alarmi listele veya aktif cihazlari getir.",
                true
        );
    }

    private ChatResponse timeAnswer() {
        return new ChatResponse("Saat " + ZonedDateTime.now(APP_ZONE).format(TIME_FORMATTER) + ".", true);
    }

    private ChatResponse dateAnswer() {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        return new ChatResponse("Bugunun tarihi " + now.format(DATE_FORMATTER) + ", saat " + now.format(TIME_FORMATTER) + ".", true);
    }

    private ChatResponse unknown() {
        return new ChatResponse("Bu istegi guvenli sekilde anlayamadim.", false);
    }

    private ChatResponse unknownWithHint() {
        return new ChatResponse("Bu istegi guvenli sekilde anlayamadim. Cihaz veya alarm verileriyle ilgili daha net bir soru sorabilirsin.", false);
    }

    private int limit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private String summarizeDevices(List<DeviceResponse> devices) {
        return devices.stream()
                .map(device -> "#" + device.getId() + " " + device.getName() + " (" + device.getStatus() + ")")
                .collect(Collectors.joining(", "));
    }

    private String summarizeAlarms(List<AlarmResponse> alarms) {
        return alarms.stream()
                .map(alarm -> "#" + alarm.getId() + " "
                        + alarm.getDeviceName() + " [cihaz #" + alarm.getDeviceId() + "] - "
                        + alarm.getAlarmType() + " (" + alarm.getSeverity() + ")")
                .collect(Collectors.joining(", "));
    }

    private String summarizeGroups(List<GroupCount> groups) {
        return groups.stream()
                .map(group -> group.key() + ": " + group.count())
                .collect(Collectors.joining(", "));
    }

    private String datePrefix(GeminiFilters filters) {
        String dateRange = defaultIfBlank(filters.getDateRange(), null);
        if (dateRange == null) {
            return "";
        }
        return switch (dateRange.toUpperCase(Locale.ROOT)) {
            case "TODAY" -> "Bugun ";
            case "YESTERDAY" -> "Dun ";
            case "LAST_24_HOURS" -> "Son 24 saatte ";
            case "LAST_7_DAYS" -> "Son 7 gunde ";
            default -> "";
        };
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toUpperCase(Locale.forLanguageTag("tr-TR")).trim();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.forLanguageTag("tr-TR")).replace('ı', 'i');
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim();
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String sessionKey(String username, String conversationId) {
        String conversation = conversationId == null || conversationId.isBlank() ? "default" : conversationId.trim();
        return username + ":" + conversation;
    }

    private record DeviceResolution(DeviceResponse device, List<DeviceResponse> matches) {
        private static DeviceResolution found(DeviceResponse device) {
            return new DeviceResolution(device, List.of());
        }

        private static DeviceResolution ambiguous(List<DeviceResponse> matches) {
            return new DeviceResolution(null, matches);
        }

        private static DeviceResolution notFound() {
            return new DeviceResolution(null, List.of());
        }

        private boolean ambiguous() {
            return !matches.isEmpty();
        }

        private boolean isNotFound() {
            return device == null && matches.isEmpty();
        }
    }

    private enum PendingCommandType {
        UPDATE_DEVICE_STATUS,
        CREATE_DEVICE,
        CREATE_ALARM,
        RESOLVE_ALARM
    }

    private record PendingCommand(
            PendingCommandType type,
            Long deviceId,
            Long alarmId,
            String deviceName,
            String deviceType,
            String status,
            String alarmType,
            String severity,
            String description,
            String confirmationText
    ) {
        private static PendingCommand deviceStatus(Long deviceId,
                                                   String deviceName,
                                                   String status,
                                                   String confirmationText) {
            return new PendingCommand(
                    PendingCommandType.UPDATE_DEVICE_STATUS,
                    deviceId,
                    null,
                    deviceName,
                    null,
                    status,
                    null,
                    null,
                    null,
                    confirmationText
            );
        }

        private static PendingCommand createDevice(String deviceName,
                                                   String deviceType,
                                                   String status,
                                                   String confirmationText) {
            return new PendingCommand(
                    PendingCommandType.CREATE_DEVICE,
                    null,
                    null,
                    deviceName,
                    deviceType,
                    status,
                    null,
                    null,
                    null,
                    confirmationText
            );
        }

        private static PendingCommand createAlarm(Long deviceId,
                                                  String deviceName,
                                                  String alarmType,
                                                  String severity,
                                                  String description,
                                                  String confirmationText) {
            return new PendingCommand(
                    PendingCommandType.CREATE_ALARM,
                    deviceId,
                    null,
                    deviceName,
                    null,
                    null,
                    alarmType,
                    severity,
                    description,
                    confirmationText
            );
        }

        private static PendingCommand resolveAlarm(Long alarmId, String confirmationText) {
            return new PendingCommand(
                    PendingCommandType.RESOLVE_ALARM,
                    null,
                    alarmId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    confirmationText
            );
        }
    }
}
