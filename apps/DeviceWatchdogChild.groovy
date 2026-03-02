definition(
    name: "Device Watchdog Child",
    namespace: "local",
    parent: "local:Device Watchdog",
    author: "Marc",
    description: "Monitors one device. Alerts on OFFLINE and also alerts if the device is ON-LINE but switched OFF. Can optionally force a switch back ON.",
    category: "Convenience",
    singleInstance: false,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    importUrl: "https://raw.githubusercontent.com/Fernsatron83/Third-Reality-Gen-2-Smart-Plug-Device-Watchdog/claude/hubitat-smart-plug-driver-KajZ7/apps/DeviceWatchdogChild.groovy"
)
preferences {
    page(name: "mainPage")
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Watchdog Instance", install: true, uninstall: true) {
        section("Instance naming") {
            input "autoNameInstance", "bool",
                title: "Auto-name this watchdog from the selected device",
                required: true, defaultValue: true, submitOnChange: true
            if (settings.autoNameInstance) {
                input "namePrefix", "text",
                    title: "Optional name prefix",
                    required: false, defaultValue: "Watchdog"
            }
        }
        section("Device") {
            input "targetDevice", "capability.switch",
                title: "Device to monitor (must support lastSeen)",
                required: true, multiple: false, submitOnChange: true
            input "deviceLabelForMessages", "text",
                title: "Friendly name for messages (optional)",
                required: false
        }
        section("Health check schedule") {
            input "checkIntervalSeconds", "number",
                title: "Health check interval (seconds)",
                required: true, defaultValue: 60
        }
        section("Offline detection (no lastSeen updates)") {
            input "offlineAfterMinutes", "number",
                title: "Declare OFFLINE after (minutes) without lastSeen updates",
                required: true, defaultValue: 5
            input "offlineNotifyRepeatMinutes", "number",
                title: "Repeat OFFLINE notification every (minutes)",
                required: true, defaultValue: 15
            input "notifyOnBackOnline", "bool",
                title: "Notify when device comes back ONLINE",
                required: true, defaultValue: true
            input "onlineNotifyCooldownSeconds", "number",
                title: "Suppress repeated ONLINE notifications for (seconds)",
                required: true, defaultValue: 120
        }
        section("Off detection (device is online but switched OFF)") {
            input "enableOffMonitoring", "bool",
                title: "Enable OFF monitoring",
                required: true, defaultValue: true, submitOnChange: true
            if (settings.enableOffMonitoring) {
                input "offAfterSeconds", "number",
                    title: "Declare OFF after (seconds) switch remains off",
                    required: true, defaultValue: 10
                input "offNotifyRepeatMinutes", "number",
                    title: "Repeat OFF notification every (minutes)",
                    required: true, defaultValue: 5
                input "notifyOnBackOn", "bool",
                    title: "Notify when device turns back ON",
                    required: true, defaultValue: true
            }
        }
        section("Messages") {
            input "offlineMessageTemplate", "text",
                title: "Offline message",
                required: true,
                defaultValue: "Uh Oh! {device} is OFFLINE and may have lost power! No updates for {offlineAfter} minutes."
            input "offlineReminderTemplate", "text",
                title: "Offline reminder message",
                required: true,
                defaultValue: "Uh Oh! {device} is still OFFLINE and may have lost power! No updates for {offlineAfter} minutes."
            input "onlineMessageTemplate", "text",
                title: "Back online message",
                required: true,
                defaultValue: "{device} is back ONLINE!"
            input "offMessageTemplate", "text",
                title: "Switched OFF message",
                required: true,
                defaultValue: "Uh Oh! {device} is OFF but still online. It may not be providing power."
            input "offReminderTemplate", "text",
                title: "Switched OFF reminder message",
                required: true,
                defaultValue: "Reminder: {device} is still OFF but online. It may not be providing power."
            input "backOnMessageTemplate", "text",
                title: "Back ON message",
                required: true,
                defaultValue: "{device} is back ON."
        }
        section("Notification endpoints") {
            input "notificationDevices", "capability.notification",
                title: "Push notification devices",
                required: false, multiple: true
            input "speechDevices", "capability.speechSynthesis",
                title: "Speech (TTS) devices",
                required: false, multiple: true
            input "audioDevices", "capability.audioNotification",
                title: "Audio notification devices",
                required: false, multiple: true
        }
        section("Start-up behavior (when device comes back online)") {
            input "startUpOnOff", "enum",
                title: "Command to send when device comes back ONLINE",
                required: true, defaultValue: "on",
                options: ["on": "Turn ON", "off": "Turn OFF", "none": "Do nothing"]
        }
        section("Optional: keep switch ON") {
            input "enableAutoRestoreOn", "bool",
                title: "If device is online but switch is off, keep sending ON",
                required: true, defaultValue: true
            input "restoreOnRepeatSeconds", "number",
                title: "Repeat ON command every (seconds) while online but off",
                required: true, defaultValue: 60
        }
        section("Logging") {
            input "logEnable", "bool",
                title: "Enable debug logging",
                required: true, defaultValue: false
        }
    }
}
/* ==========================
   Lifecycle
   ========================== */
def installed() {
    initialize()
}
def updated() {
    unschedule()
    unsubscribe()
    initialize()
}
def initialize() {
    maybeAutoLabel()
    state.isOffline = null
    state.lastOfflineNotifiedAt = null
    state.lastOnlineNotifiedAt = state.lastOnlineNotifiedAt ?: null
    state.isOff = null
    state.offSinceMs = null
    state.lastOffNotifiedAt = null
    state.restoreJobActive = false
    if (targetDevice) {
        subscribe(targetDevice, "switch", switchHandler)
        subscribe(targetDevice, "lastSeen", lastSeenHandler)
    }
    scheduleHealthChecks()
    runIn(2, "healthCheckTick")
}
private void maybeAutoLabel() {
    if (!(settings.autoNameInstance as Boolean)) return
    if (!targetDevice) return
    String prefix = (settings.namePrefix as String)?.trim()
    if (!prefix) prefix = "Watchdog"
    String desired = "${prefix} - ${targetDevice.displayName}"
    if (app.label != desired) app.updateLabel(desired)
}
/* ==========================
   Scheduling and handlers
   ========================== */
private void scheduleHealthChecks() {
    Integer s = safeInt(checkIntervalSeconds, 60)
    if (s < 15) s = 15
    if (s > 3600) s = 3600
    runIn(s, "healthCheckTick")
}
def healthCheckTick() {
    Integer s = safeInt(checkIntervalSeconds, 60)
    if (s < 15) s = 15
    if (s > 3600) s = 3600
    try {
        evaluateAll("schedule")
    } finally {
        runIn(s, "healthCheckTick")
    }
}
def lastSeenHandler(evt) {
    evaluateAll("lastSeen")
}
def switchHandler(evt) {
    evaluateAll("switch")
}
/* ==========================
   Core logic
   ========================== */
private void evaluateAll(String source) {
    if (!targetDevice) return
    evaluateOffline(source)
    if (settings.enableOffMonitoring as Boolean) {
        evaluateOffState(source)
    } else {
        state.isOff = null
        state.offSinceMs = null
        state.lastOffNotifiedAt = null
    }
    if (state.isOffline == true) stopRestoreLoop()
    else maybeStartRestoreLoop()
}
/* -------- OFFLINE -------- */
private void evaluateOffline(String source) {
    Boolean offline = isDeviceOffline()
    if (state.isOffline == null) {
        state.isOffline = offline
        return
    }
    Boolean wasOffline = (state.isOffline as Boolean)
    if (offline && !wasOffline) {
        state.isOffline = true
        handleWentOffline()
        return
    }
    if (!offline && wasOffline) {
        state.isOffline = false
        handleCameOnline()
        return
    }
    if (offline) maybeRepeatOfflineNotification()
}
private Boolean isDeviceOffline() {
    Integer minutes = safeInt(offlineAfterMinutes, 5)
    if (minutes < 1) minutes = 1
    Long thresholdMs = minutes * 60_000L
    String lastSeenStr = targetDevice.currentValue("lastSeen") as String
    if (!lastSeenStr) return true
    Long lastSeenMs = parseLastSeenToMillis(lastSeenStr)
    if (lastSeenMs == null) return true
    return (now() - lastSeenMs) > thresholdMs
}
private Long parseLastSeenToMillis(String s) {
    try {
        def df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        df.setTimeZone(location.timeZone)
        Date d = df.parse(s)
        return d?.time
    } catch (e) {
        return null
    }
}
private void handleWentOffline() {
    notifyEvent("offline")
    state.lastOfflineNotifiedAt = now()
}
private void handleCameOnline() {
    state.lastOfflineNotifiedAt = null
    applyStartUpOnOff()
    if (!(settings.notifyOnBackOnline as Boolean)) return
    if (!shouldSendOnlineNow()) return
    notifyEvent("online")
    state.lastOnlineNotifiedAt = now()
}
private void applyStartUpOnOff() {
    String cmd = (settings.startUpOnOff as String) ?: "none"
    if (cmd == "on") {
        try { targetDevice.on() } catch (e) { log.warn "${app.label}: StartUpOnOff on() failed: ${e}" }
        if (logEnable) log.info "${app.label}: StartUpOnOff sent on() to ${targetDevice.displayName}"
    } else if (cmd == "off") {
        try { targetDevice.off() } catch (e) { log.warn "${app.label}: StartUpOnOff off() failed: ${e}" }
        if (logEnable) log.info "${app.label}: StartUpOnOff sent off() to ${targetDevice.displayName}"
    }
}
private Boolean shouldSendOnlineNow() {
    Integer cooldown = safeInt(onlineNotifyCooldownSeconds, 120)
    if (cooldown < 0) cooldown = 0
    Long last = (state.lastOnlineNotifiedAt != null) ? (state.lastOnlineNotifiedAt as Long) : null
    if (last == null) return true
    return (now() - last) > (cooldown * 1000L)
}
private void maybeRepeatOfflineNotification() {
    Integer repeatMin = safeInt(offlineNotifyRepeatMinutes, 15)
    if (repeatMin < 1) repeatMin = 1
    Long repeatMs = repeatMin * 60_000L
    Long last = (state.lastOfflineNotifiedAt != null) ? (state.lastOfflineNotifiedAt as Long) : null
    if (last == null) {
        state.lastOfflineNotifiedAt = now()
        return
    }
    if ((now() - last) >= repeatMs) {
        notifyEvent("offlineReminder")
        state.lastOfflineNotifiedAt = now()
    }
}
/* -------- OFF (online but switch off) -------- */
private void evaluateOffState(String source) {
    if (state.isOffline == true) {
        // If truly offline, OFF state is not evaluated.
        state.isOff = null
        state.offSinceMs = null
        state.lastOffNotifiedAt = null
        return
    }
    String sw = (targetDevice.currentValue("switch") as String) ?: "unknown"
    Boolean offCondition = (sw == "off")
    if (offCondition) {
        if (state.offSinceMs == null) state.offSinceMs = now()
        Integer afterSec = safeInt(offAfterSeconds, 10)
        if (afterSec < 0) afterSec = 0
        Boolean shouldBeOff = (now() - (state.offSinceMs as Long)) >= (afterSec * 1000L)
        if (shouldBeOff) {
            if (state.isOff != true) {
                state.isOff = true
                handleWentOff()
            } else {
                maybeRepeatOffNotification()
            }
        }
    } else {
        state.offSinceMs = null
        if (state.isOff == true) {
            state.isOff = false
            handleBackOn()
        } else {
            state.isOff = false
        }
        state.lastOffNotifiedAt = null
    }
}
private void handleWentOff() {
    notifyEvent("off")
    state.lastOffNotifiedAt = now()
}
private void handleBackOn() {
    if (!(settings.notifyOnBackOn as Boolean)) return
    notifyEvent("backOn")
}
private void maybeRepeatOffNotification() {
    Integer repeatMin = safeInt(offNotifyRepeatMinutes, 5)
    if (repeatMin < 1) repeatMin = 1
    Long repeatMs = repeatMin * 60_000L
    Long last = (state.lastOffNotifiedAt != null) ? (state.lastOffNotifiedAt as Long) : null
    if (last == null) {
        state.lastOffNotifiedAt = now()
        return
    }
    if ((now() - last) >= repeatMs) {
        notifyEvent("offReminder")
        state.lastOffNotifiedAt = now()
    }
}
/* -------- Restore loop -------- */
private void maybeStartRestoreLoop() {
    if (!(settings.enableAutoRestoreOn as Boolean)) return
    if (state.isOffline == true) return
    String sw = targetDevice.currentValue("switch") as String
    if (sw == "on") { stopRestoreLoop(); return }
    if (state.restoreJobActive != true) {
        state.restoreJobActive = true
        restoreTick()
    }
}
def restoreTick() {
    if (!(settings.enableAutoRestoreOn as Boolean)) { stopRestoreLoop(); return }
    if (state.isOffline == true) { stopRestoreLoop(); return }
    String sw = targetDevice.currentValue("switch") as String
    if (sw == "on") { stopRestoreLoop(); return }
    try { targetDevice.on() } catch (e) { }
    Integer s = safeInt(restoreOnRepeatSeconds, 60)
    if (s < 15) s = 15
    if (s > 3600) s = 3600
    runIn(s, "restoreTick")
}
private void stopRestoreLoop() {
    if (state.restoreJobActive == true) {
        state.restoreJobActive = false
        unschedule("restoreTick")
    }
}
/* ==========================
   Notifications and templates
   ========================== */
private void notifyEvent(String type) {
    String msg = renderTemplate(getTemplate(type))
    sendToEndpoints(msg)
    if (logEnable) log.info "${app.label}: Notified (${type}): ${msg}"
}
private String getTemplate(String type) {
    if (type == "offline") return offlineMessageTemplate
    if (type == "offlineReminder") return offlineReminderTemplate
    if (type == "online") return onlineMessageTemplate
    if (type == "off") return offMessageTemplate
    if (type == "offReminder") return offReminderTemplate
    if (type == "backOn") return backOnMessageTemplate
    return "{device} watchdog event"
}
private void sendToEndpoints(String msg) {
    notificationDevices?.each { dev ->
        try { dev.deviceNotification(msg) } catch (e) { log.warn "${app.label}: Push failed (${dev?.displayName}): ${e}" }
    }
    speechDevices?.each { dev ->
        try { dev.speak(msg) } catch (e) { log.warn "${app.label}: Speech failed (${dev?.displayName}): ${e}" }
    }
    audioDevices?.each { dev ->
        try { dev.playText(msg) } catch (e) { log.warn "${app.label}: Audio failed (${dev?.displayName}): ${e}" }
    }
    if (!notificationDevices && !speechDevices && !audioDevices) {
        log.warn "${app.label}: No notification endpoints configured. Message: ${msg}"
    }
}
private String renderTemplate(String template) {
    String deviceName = getDeviceNameForMessages()
    String nowStr = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    String out = template ?: ""
    out = out.replace("{device}", deviceName)
    out = out.replace("{offlineAfter}", "${safeInt(offlineAfterMinutes, 5)}")
    out = out.replace("{now}", nowStr)
    return out
}
private String getDeviceNameForMessages() {
    String friendly = (settings.deviceLabelForMessages as String)
    if (friendly?.trim()) return friendly.trim()
    return targetDevice?.displayName ?: "Device"
}
private Integer safeInt(val, Integer defVal) {
    try { return (val == null) ? defVal : (val as Integer) } catch (e) { return defVal }
}
