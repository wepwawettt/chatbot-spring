const state = {
    users: [],
    devices: [],
    alarms: [],
    selectedUserId: "",
    selectedAlarmDeviceId: "",
    chatEntries: [],
    chatConversationId: sessionStorage.getItem("chatConversationId") || newConversationId(),
    authToken: sessionStorage.getItem("authToken") || "",
    currentUser: null
};

sessionStorage.setItem("chatConversationId", state.chatConversationId);

const elements = {
    refreshButton: document.querySelector("#refreshButton"),
    logoutButton: document.querySelector("#logoutButton"),
    currentUser: document.querySelector("#currentUser"),
    loginPanel: document.querySelector("#loginPanel"),
    appContent: document.querySelector("#appContent"),
    loginForm: document.querySelector("#loginForm"),
    chatForm: document.querySelector("#chatForm"),
    chatMessages: document.querySelector("#chatMessages"),
    newChatButton: document.querySelector("#newChatButton"),
    swaggerLink: document.querySelector("a[href='/swagger-ui.html']"),
    apiStatus: document.querySelector("#apiStatus"),
    toast: document.querySelector("#toast"),
    userCount: document.querySelector("#userCount"),
    deviceCount: document.querySelector("#deviceCount"),
    activeDeviceCount: document.querySelector("#activeDeviceCount"),
    alarmCount: document.querySelector("#alarmCount"),
    userMetricLabel: document.querySelector("#userMetricLabel"),
    deviceMetricLabel: document.querySelector("#deviceMetricLabel"),
    usersPanelTitle: document.querySelector("#usersPanelTitle"),
    devicesPanelTitle: document.querySelector("#devicesPanelTitle"),
    userDevicesPanelTitle: document.querySelector("#userDevicesPanelTitle"),
    usersTable: document.querySelector("#usersTable"),
    devicesTable: document.querySelector("#devicesTable"),
    userDevicesTable: document.querySelector("#userDevicesTable"),
    alarmsTable: document.querySelector("#alarmsTable"),
    userForm: document.querySelector("#userForm"),
    deviceForm: document.querySelector("#deviceForm"),
    alarmForm: document.querySelector("#alarmForm"),
    assignForm: document.querySelector("#assignForm"),
    deviceSubmitButton: document.querySelector("#deviceSubmitButton"),
    deviceCancelButton: document.querySelector("#deviceCancelButton"),
    alarmDeviceSelect: document.querySelector("#alarmDeviceSelect"),
    assignUserSelect: document.querySelector("#assignUserSelect"),
    assignDeviceSelect: document.querySelector("#assignDeviceSelect"),
    userDevicesSelect: document.querySelector("#userDevicesSelect"),
    alarmsDeviceSelect: document.querySelector("#alarmsDeviceSelect"),
    formsPanelTitle: document.querySelector("#formsPanelTitle")
};

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    boot();
});

window.addEventListener("pagehide", () => {
    cleanupChatHistory(true);
});

function bindEvents() {
    elements.refreshButton.addEventListener("click", loadDashboard);
    elements.logoutButton.addEventListener("click", logout);
    elements.loginForm.addEventListener("submit", login);
    elements.chatForm.addEventListener("submit", sendChatMessage);
    elements.newChatButton.addEventListener("click", startNewChat);

    document.querySelectorAll(".tab").forEach((tab) => {
        tab.addEventListener("click", () => activateTab(tab.dataset.tab));
    });

    elements.userForm.addEventListener("submit", createUser);
    elements.deviceForm.addEventListener("submit", saveDevice);
    elements.alarmForm.addEventListener("submit", createAlarm);
    elements.assignForm.addEventListener("submit", assignDevice);
    elements.deviceCancelButton.addEventListener("click", resetDeviceForm);

    elements.userDevicesSelect.addEventListener("change", async (event) => {
        state.selectedUserId = event.target.value;
        await renderUserDevices();
    });

    elements.alarmsDeviceSelect.addEventListener("change", async (event) => {
        state.selectedAlarmDeviceId = event.target.value;
        await renderAlarms();
    });
}

async function boot() {
    if (!state.authToken) {
        showLogin();
        return;
    }

    try {
        await loadCurrentUser();
        showApp();
        await loadDashboard();
        renderChatMessages();
    } catch (error) {
        logout();
        showToast("Oturum bilgisi gecersiz", true);
    }
}

async function login(event) {
    event.preventDefault();
    const form = new FormData(elements.loginForm);
    const username = form.get("username");
    const password = form.get("password");

    state.authToken = btoa(`${username}:${password}`);
    sessionStorage.setItem("authToken", state.authToken);

    try {
        await loadCurrentUser();
        elements.loginForm.reset();
        showApp();
        await loadDashboard();
        renderChatMessages();
        showToast("Giris yapildi");
    } catch (error) {
        logout();
        showToast("Kullanici adi veya sifre hatali", true);
    }
}

async function loadCurrentUser() {
    state.currentUser = await request("/api/auth/me");
    elements.currentUser.textContent = `${state.currentUser.username} / ${state.currentUser.role}`;
}

function logout() {
    cleanupChatHistory(false);
    resetLocalSession();
}

function resetLocalSession() {
    sessionStorage.removeItem("authToken");
    state.authToken = "";
    state.currentUser = null;
    state.users = [];
    state.devices = [];
    state.alarms = [];
    state.selectedUserId = "";
    state.selectedAlarmDeviceId = "";
    state.chatEntries = [];
    showLogin();
}

async function cleanupChatHistory(keepalive, token = state.authToken) {
    if (!token) {
        return;
    }

    try {
        await fetch("/api/chat/conversations", {
            method: "DELETE",
            headers: {
                Authorization: `Basic ${token}`
            },
            keepalive
        });
    } catch {
        // Page close cleanup is best-effort; the next login can still continue normally.
    }

    state.chatEntries = [];
    state.chatConversationId = newConversationId();
    sessionStorage.setItem("chatConversationId", state.chatConversationId);
}

function showLogin() {
    document.body.classList.remove("role-admin", "role-user");
    elements.loginPanel.classList.remove("hidden");
    elements.appContent.classList.add("hidden");
    elements.currentUser.classList.add("hidden");
    elements.logoutButton.classList.add("hidden");
    elements.refreshButton.classList.add("hidden");
    setApiStatus("Giris bekleniyor");
}

function showApp() {
    elements.loginPanel.classList.add("hidden");
    elements.appContent.classList.remove("hidden");
    elements.currentUser.classList.remove("hidden");
    elements.logoutButton.classList.remove("hidden");
    elements.refreshButton.classList.remove("hidden");
    applyRoleUi();
}

function isAdmin() {
    return state.currentUser?.role === "ADMIN";
}

function canDeleteUser(user) {
    return isAdmin()
        && user.username !== state.currentUser?.username
        && user.role !== "ADMIN";
}

function applyRoleUi() {
    const admin = isAdmin();
    const adminOnlyTabs = new Set(["userFormPanel", "deviceFormPanel", "assignFormPanel"]);
    document.body.classList.toggle("role-admin", admin);
    document.body.classList.toggle("role-user", !admin);

    document.querySelectorAll(".tab").forEach((tab) => {
        tab.classList.toggle("hidden", adminOnlyTabs.has(tab.dataset.tab) && !admin);
    });

    if (!admin) {
        activateTab("alarmFormPanel");
    }

    elements.formsPanelTitle.textContent = admin ? "Kayit islemleri" : "Alarm islemleri";
    elements.userMetricLabel.textContent = admin ? "Kullanicilar" : "Profil";
    elements.deviceMetricLabel.textContent = admin ? "Cihazlar" : "Cihazlarim";
    elements.usersPanelTitle.textContent = admin ? "Kullanicilar" : "Profil";
    elements.devicesPanelTitle.textContent = admin ? "Cihazlar" : "Cihazlarim";
    elements.userDevicesPanelTitle.textContent = admin ? "Kullanici cihazlari" : "Cihaz atamalari";
    elements.swaggerLink.classList.toggle("hidden", !admin);
}

async function loadDashboard() {
    setApiStatus("Yukleniyor");

    try {
        const [users, devices] = await Promise.all([
            request("/api/users"),
            request("/api/devices")
        ]);

        state.users = users;
        state.devices = devices;

        syncSelections(users, devices);

        state.alarms = await loadAllAlarms(devices);

        renderAll();
        setApiStatus("Bagli");
    } catch (error) {
        setApiStatus("Baglanti yok");
        showToast(error.message, true);
    }
}

function syncSelections(users, devices) {
    const selectedUserVisible = users.some((user) => String(user.id) === String(state.selectedUserId));
    const selectedAlarmDeviceVisible = devices.some((device) => String(device.id) === String(state.selectedAlarmDeviceId));

    if (!isAdmin()) {
        const currentUser = users.find((user) => String(user.id) === String(state.currentUser?.id));
        state.selectedUserId = currentUser ? String(currentUser.id) : (users[0] ? String(users[0].id) : "");
    } else if (!selectedUserVisible) {
        state.selectedUserId = users[0] ? String(users[0].id) : "";
    }

    if (!selectedAlarmDeviceVisible) {
        state.selectedAlarmDeviceId = devices[0] ? String(devices[0].id) : "";
    }
}

function renderChatMessages() {
    if (state.chatEntries.length === 0) {
        elements.chatMessages.innerHTML = `<div class="empty">Alarm verilerini natural language ile sorgulayabilirsin.</div>`;
        return;
    }

    elements.chatMessages.innerHTML = state.chatEntries.map((message) => `
        <div class="chat-bubble user">${escapeHtml(message.userMessage)}</div>
        <div class="chat-bubble assistant ${message.pending ? "pending" : ""}">
            ${renderAssistantMessage(message.assistantMessage)}
        </div>
    `).join("");
    elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
}

function renderAssistantMessage(value) {
    if (value && typeof value === "object") {
        return renderAssistantValue(value);
    }

    const text = String(value ?? "").trim();
    const parsedJson = parseJsonText(text);
    if (parsedJson !== undefined) {
        return renderAssistantValue(parsedJson);
    }

    return renderTextAnswer(text);
}

function renderAssistantValue(value) {
    const readableText = value?.answer ?? value?.message ?? value?.error;
    if (typeof readableText === "string") {
        return renderTextAnswer(readableText);
    }

    return renderJsonValue(value);
}

function renderTextAnswer(text) {
    if (!text) {
        return "";
    }

    return text
        .split(/\n{2,}/)
        .map((section) => section.trim())
        .filter(Boolean)
        .map(renderTextSection)
        .join("");
}

function renderTextSection(section) {
    const lines = section
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
    let html = "";
    let listItems = [];

    const flushList = () => {
        if (listItems.length === 0) {
            return;
        }
        html += `<ul class="answer-list">${listItems.map((item) => `<li>${formatInlineText(item)}</li>`).join("")}</ul>`;
        listItems = [];
    };

    lines.forEach((line) => {
        if (/^[-*]\s+/.test(line)) {
            listItems.push(line.replace(/^[-*]\s+/, ""));
            return;
        }

        flushList();
        html += `<p>${formatInlineText(line)}</p>`;
    });

    flushList();
    return html;
}

function renderJsonValue(value) {
    if (Array.isArray(value)) {
        if (value.length === 0) {
            return `<p>Kayit yok.</p>`;
        }
        return `<ul class="answer-list">${value.map((item) => `<li>${renderJsonValue(item)}</li>`).join("")}</ul>`;
    }

    if (value && typeof value === "object") {
        const entries = Object.entries(value);
        if (entries.length === 0) {
            return `<p>Kayit yok.</p>`;
        }
        return `<dl class="answer-fields">${entries.map(([key, item]) => `
            <dt>${escapeHtml(humanizeKey(key))}</dt>
            <dd>${renderJsonValue(item)}</dd>
        `).join("")}</dl>`;
    }

    return `<span>${formatInlineText(String(value ?? "-"))}</span>`;
}

function parseJsonText(text) {
    if (!text || !/^\s*[\[{]/.test(text)) {
        return undefined;
    }

    try {
        return JSON.parse(text);
    } catch {
        return undefined;
    }
}

function formatInlineText(value) {
    return escapeHtml(value).replace(/^([^:]{2,48}:)(\s*)/, "<strong>$1</strong>$2");
}

function humanizeKey(value) {
    const label = String(value ?? "")
        .replace(/([a-z])([A-Z])/g, "$1 $2")
        .replaceAll("_", " ")
        .trim();
    return label ? label.charAt(0).toUpperCase() + label.slice(1) : "-";
}

async function sendChatMessage(event) {
    event.preventDefault();
    const form = new FormData(elements.chatForm);
    const message = form.get("message");
    const entry = {
        userMessage: message,
        assistantMessage: "Cevap hazirlaniyor...",
        pending: true
    };
    state.chatEntries.push(entry);
    renderChatMessages();
    elements.chatForm.reset();

    try {
        const response = await request("/api/chat", {
            method: "POST",
            body: {
                message,
                conversationId: state.chatConversationId
            }
        });
        if (response.conversationId) {
            state.chatConversationId = response.conversationId;
            sessionStorage.setItem("chatConversationId", state.chatConversationId);
        }
        entry.assistantMessage = response.answer || "Bu istegi guvenli sekilde anlayamadim.";
        entry.pending = false;
        renderChatMessages();
        if (response.success) {
            await loadDashboard();
        }
    } catch (error) {
        entry.assistantMessage = error.message;
        entry.pending = false;
        renderChatMessages();
        showToast(error.message, true);
    }
}

function startNewChat() {
    state.chatEntries = [];
    state.chatConversationId = newConversationId();
    sessionStorage.setItem("chatConversationId", state.chatConversationId);
    renderChatMessages();
}

async function loadAllAlarms(devices) {
    const results = await Promise.all(
        devices.map((device) =>
            request(`/api/devices/${device.id}/alarms`).catch(() => [])
        )
    );

    return results
        .flat()
        .sort((a, b) => new Date(b.occurredAt || 0) - new Date(a.occurredAt || 0));
}

function renderAll() {
    renderMetrics();
    renderSelects();
    renderUsers();
    renderDevices();
    if (isAdmin()) {
        renderUserDevices();
    } else {
        elements.userDevicesTable.innerHTML = "";
    }
    renderAlarms();
}

function renderMetrics() {
    elements.userCount.textContent = state.users.length;
    elements.deviceCount.textContent = state.devices.length;
    elements.activeDeviceCount.textContent = state.devices.filter((device) => device.status === "ACTIVE").length;
    elements.alarmCount.textContent = state.alarms.length;
}

function renderSelects() {
    renderOptions(elements.assignUserSelect, state.users, "Kullanici yok", userLabel);
    renderOptions(elements.userDevicesSelect, state.users, "Kullanici yok", userLabel);
    renderOptions(elements.assignDeviceSelect, state.devices, "Cihaz yok", deviceLabel);
    renderOptions(elements.alarmDeviceSelect, state.devices, "Cihaz yok", deviceLabel);
    renderOptions(elements.alarmsDeviceSelect, state.devices, "Cihaz yok", deviceLabel);

    elements.userDevicesSelect.value = state.selectedUserId;
    elements.alarmsDeviceSelect.value = state.selectedAlarmDeviceId;
}

function renderOptions(select, items, emptyText, labelFn) {
    select.innerHTML = "";

    if (items.length === 0) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = emptyText;
        select.append(option);
        select.disabled = true;
        return;
    }

    select.disabled = false;
    items.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.id;
        option.textContent = labelFn(item);
        select.append(option);
    });
}

function renderUsers() {
    if (state.users.length === 0) {
        renderEmpty(elements.usersTable, isAdmin() ? 5 : 4, "Kayitli kullanici yok");
        return;
    }

    const actionCell = (user) => isAdmin() ? `
            <td class="admin-action-col">
                <div class="row-actions ${canDeleteUser(user) ? "" : "hidden"}">
                    <button class="danger-button" type="button" data-action="delete-user" data-id="${user.id}">Sil</button>
                </div>
            </td>
    ` : "";

    elements.usersTable.innerHTML = state.users.map((user) => `
        <tr>
            <td>${escapeHtml(user.id)}</td>
            <td>${escapeHtml(user.username)}</td>
            <td>${escapeHtml(user.email)}</td>
            <td>${escapeHtml(user.role || "USER")}</td>
            ${actionCell(user)}
        </tr>
    `).join("");

    elements.usersTable.querySelectorAll("[data-action='delete-user']").forEach((button) => {
        button.addEventListener("click", () => deleteUser(button.dataset.id));
    });
}

function renderDevices() {
    if (state.devices.length === 0) {
        renderEmpty(elements.devicesTable, isAdmin() ? 6 : 5, "Kayitli cihaz yok");
        return;
    }

    const actionCell = (device) => isAdmin() ? `
            <td class="admin-action-col">
                <div class="row-actions">
                    <button class="small-button" type="button" data-action="edit-device" data-id="${device.id}">Duzenle</button>
                    <button class="danger-button" type="button" data-action="delete-device" data-id="${device.id}">Sil</button>
                </div>
            </td>
    ` : "";

    elements.devicesTable.innerHTML = state.devices.map((device) => `
        <tr>
            <td>${escapeHtml(device.id)}</td>
            <td>${escapeHtml(device.name)}</td>
            <td>${escapeHtml(device.deviceType)}</td>
            <td>${statusBadge(device.status)}</td>
            <td>${escapeHtml(device.location || "-")}</td>
            ${actionCell(device)}
        </tr>
    `).join("");

    elements.devicesTable.querySelectorAll("[data-action='edit-device']").forEach((button) => {
        button.addEventListener("click", () => startEditDevice(button.dataset.id));
    });

    elements.devicesTable.querySelectorAll("[data-action='delete-device']").forEach((button) => {
        button.addEventListener("click", () => deleteDevice(button.dataset.id));
    });
}

async function renderUserDevices() {
    const userId = state.selectedUserId;
    if (!userId) {
        renderEmpty(elements.userDevicesTable, isAdmin() ? 4 : 3, "Once kullanici ekleyin");
        return;
    }

    try {
        const devices = await request(`/api/users/${userId}/devices`);
        if (devices.length === 0) {
            renderEmpty(elements.userDevicesTable, isAdmin() ? 4 : 3, "Bu kullaniciya atanmis cihaz yok");
            return;
        }

        const actionCell = (device) => isAdmin() ? `
                <td class="admin-action-col">
                    <div class="row-actions">
                        <button class="danger-button" type="button" data-action="detach-device" data-id="${device.id}">Kaldir</button>
                    </div>
                </td>
        ` : "";

        elements.userDevicesTable.innerHTML = devices.map((device) => `
            <tr>
                <td>${escapeHtml(device.id)}</td>
                <td>${escapeHtml(device.name)}</td>
                <td>${statusBadge(device.status)}</td>
                ${actionCell(device)}
            </tr>
        `).join("");

        elements.userDevicesTable.querySelectorAll("[data-action='detach-device']").forEach((button) => {
            button.addEventListener("click", () => detachDevice(userId, button.dataset.id));
        });
    } catch (error) {
        renderEmpty(elements.userDevicesTable, isAdmin() ? 4 : 3, error.message);
    }
}

async function renderAlarms() {
    const deviceId = state.selectedAlarmDeviceId;
    const alarms = deviceId
        ? state.alarms.filter((alarm) => String(alarm.deviceId) === String(deviceId))
        : state.alarms;

    if (alarms.length === 0) {
        renderEmpty(elements.alarmsTable, 5, "Alarm kaydi yok");
        return;
    }

    elements.alarmsTable.innerHTML = alarms.map((alarm) => `
        <tr>
            <td>${escapeHtml(alarm.id)}</td>
            <td>${escapeHtml(alarm.deviceName || alarm.deviceId)}</td>
            <td>${escapeHtml(alarm.alarmType)}</td>
            <td>${severityBadge(alarm.severity)}</td>
            <td>${escapeHtml(formatDate(alarm.occurredAt))}</td>
        </tr>
    `).join("");
}

async function createUser(event) {
    event.preventDefault();
    const form = new FormData(elements.userForm);

    try {
        await request("/api/users", {
            method: "POST",
            body: {
                username: form.get("username"),
                email: form.get("email"),
                password: form.get("password"),
                role: form.get("role") || null
            }
        });
        elements.userForm.reset();
        showToast("Kullanici eklendi");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function saveDevice(event) {
    event.preventDefault();
    const form = new FormData(elements.deviceForm);
    const id = form.get("id");

    const body = {
        name: form.get("name"),
        deviceType: form.get("deviceType"),
        location: form.get("location") || null
    };

    if (id) {
        body.status = form.get("status");
    }

    try {
        await request(id ? `/api/devices/${id}` : "/api/devices", {
            method: id ? "PUT" : "POST",
            body
        });
        resetDeviceForm();
        showToast(id ? "Cihaz guncellendi" : "Cihaz eklendi");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function createAlarm(event) {
    event.preventDefault();
    const form = new FormData(elements.alarmForm);

    try {
        await request("/api/alarms", {
            method: "POST",
            body: {
                deviceId: Number(form.get("deviceId")),
                alarmType: form.get("alarmType"),
                severity: form.get("severity"),
                description: form.get("description")
            }
        });
        elements.alarmForm.reset();
        showToast("Alarm olusturuldu");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function assignDevice(event) {
    event.preventDefault();
    const form = new FormData(elements.assignForm);
    const userId = form.get("userId");
    const deviceId = form.get("deviceId");

    try {
        await request(`/api/users/${userId}/devices`, {
            method: "POST",
            body: { deviceId: Number(deviceId) }
        });
        state.selectedUserId = String(userId);
        showToast("Cihaz kullaniciya atandi");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function detachDevice(userId, deviceId) {
    try {
        await request(`/api/users/${userId}/devices/${deviceId}`, { method: "DELETE" });
        showToast("Cihaz atamasi kaldirildi");
        await renderUserDevices();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function deleteUser(id) {
    try {
        await request(`/api/users/${id}`, { method: "DELETE" });
        if (state.selectedUserId === String(id)) {
            state.selectedUserId = "";
        }
        showToast("Kullanici silindi");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

async function deleteDevice(id) {
    try {
        await request(`/api/devices/${id}`, { method: "DELETE" });
        if (state.selectedAlarmDeviceId === String(id)) {
            state.selectedAlarmDeviceId = "";
        }
        showToast("Cihaz silindi");
        await loadDashboard();
    } catch (error) {
        showToast(error.message, true);
    }
}

function startEditDevice(id) {
    const device = state.devices.find((item) => String(item.id) === String(id));
    if (!device) {
        return;
    }

    activateTab("deviceFormPanel");
    elements.deviceForm.elements.id.value = device.id;
    elements.deviceForm.elements.name.value = device.name || "";
    elements.deviceForm.elements.deviceType.value = device.deviceType || "CAMERA";
    elements.deviceForm.elements.status.value = device.status || "ACTIVE";
    elements.deviceForm.elements.location.value = device.location || "";
    elements.deviceSubmitButton.textContent = "Cihazi guncelle";
    elements.deviceCancelButton.classList.remove("hidden");
    elements.deviceForm.scrollIntoView({ behavior: "smooth", block: "center" });
}

function resetDeviceForm() {
    elements.deviceForm.reset();
    elements.deviceForm.elements.id.value = "";
    elements.deviceForm.elements.status.value = "ACTIVE";
    elements.deviceSubmitButton.textContent = "Cihaz ekle";
    elements.deviceCancelButton.classList.add("hidden");
}

async function request(path, options = {}) {
    const headers = {};
    if (options.body) {
        headers["Content-Type"] = "application/json";
    }
    if (state.authToken) {
        headers.Authorization = `Basic ${state.authToken}`;
    }

    const response = await fetch(path, {
        method: options.method || "GET",
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined
    });

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = { error: text };
        }
    }

    if (!response.ok) {
        throw new Error(data?.error || `Istek basarisiz: ${response.status}`);
    }

    return data;
}

function activateTab(panelName) {
    document.querySelectorAll(".tab").forEach((tab) => {
        tab.classList.toggle("active", tab.dataset.tab === panelName);
    });

    document.querySelectorAll(".form-section").forEach((panel) => {
        panel.classList.toggle("active", panel.dataset.panel === panelName);
    });
}

function renderEmpty(tableBody, colspan, message) {
    tableBody.innerHTML = `
        <tr>
            <td class="empty" colspan="${colspan}">${escapeHtml(message)}</td>
        </tr>
    `;
}

function statusBadge(status) {
    const value = status || "UNKNOWN";
    return `<span class="badge ${escapeHtml(value.toLowerCase())}">${escapeHtml(value)}</span>`;
}

function severityBadge(severity) {
    const value = severity || "MEDIUM";
    return `<span class="badge ${escapeHtml(value.toLowerCase())}">${escapeHtml(value)}</span>`;
}

function userLabel(user) {
    return `#${user.id} ${user.username}`;
}

function deviceLabel(device) {
    return `#${device.id} ${device.name}`;
}

function formatDate(value) {
    if (!value) {
        return "-";
    }

    return new Intl.DateTimeFormat("tr-TR", {
        dateStyle: "short",
        timeStyle: "short"
    }).format(new Date(value));
}

function setApiStatus(value) {
    elements.apiStatus.textContent = value;
}

function showToast(message, isError = false) {
    elements.toast.textContent = message;
    elements.toast.classList.toggle("error", isError);
    elements.toast.classList.add("show");

    window.clearTimeout(showToast.timeout);
    showToast.timeout = window.setTimeout(() => {
        elements.toast.classList.remove("show");
    }, 3200);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function newConversationId() {
    if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
