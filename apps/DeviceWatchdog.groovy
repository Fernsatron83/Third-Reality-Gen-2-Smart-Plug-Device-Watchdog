definition(
    name: "Device Watchdog",
    namespace: "local",
    author: "Marc",
    description: "Parent app. Create one watchdog instance per device.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    importUrl: "https://raw.githubusercontent.com/Fernsatron83/Third-Reality-Gen-2-Smart-Plug-Device-Watchdog/master/apps/DeviceWatchdog.groovy"
)
preferences {
    page(name: "mainPage")
    page(name: "influxSettingsPage")
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Device Watchdog", install: true, uninstall: true) {
        section("Watchdog instances") {
            app(
                name: "childApps",
                appName: "Device Watchdog Child",
                namespace: "local",
                title: "Add new watchdog",
                multiple: true
            )
        }
        section("Data logging") {
            href "influxSettingsPage", title: "InfluxDB Settings",
                description: influxSettingsSummary()
        }
    }
}
def influxSettingsPage() {
    dynamicPage(name: "influxSettingsPage", title: "InfluxDB Logging") {
        section("Connection") {
            input "influxEnabled", "bool",
                title: "Enable InfluxDB logging",
                required: true, defaultValue: false, submitOnChange: true
            if (settings.influxEnabled) {
                input "influxUrl", "text",
                    title: "InfluxDB URL (e.g. http://192.168.1.100:8086)",
                    required: true
                input "influxToken", "text",
                    title: "API token",
                    required: true
                input "influxOrg", "text",
                    title: "Organization",
                    required: true
                input "influxBucket", "text",
                    title: "Bucket",
                    required: true
            }
        }
        if (settings.influxEnabled) {
            section("Enable logging for these watchdog instances") {
                def children = getChildApps()
                if (children) {
                    children.sort { it.label ?: it.name }.each { child ->
                        input "influxEnable_${child.id}", "bool",
                            title: "${child.label ?: child.name}",
                            required: false, defaultValue: false
                    }
                } else {
                    paragraph "No watchdog instances created yet. Create instances first, then enable logging here."
                }
            }
        }
    }
}
private String influxSettingsSummary() {
    if (!(settings.influxEnabled as Boolean)) return "Tap to configure InfluxDB data logging"
    int count = 0
    getChildApps().each { child ->
        if (settings["influxEnable_${child.id}"] as Boolean) count++
    }
    return "Enabled \u2014 ${count} instance(s) logging to ${settings.influxBucket ?: '?'}"
}
/* ==========================
   InfluxDB config for children
   ========================== */
Map getInfluxConfig() {
    if (!(settings.influxEnabled as Boolean)) return null
    if (!settings.influxUrl || !settings.influxToken || !settings.influxOrg || !settings.influxBucket) return null
    return [
        url:    settings.influxUrl,
        token:  settings.influxToken,
        org:    settings.influxOrg,
        bucket: settings.influxBucket
    ]
}
Boolean isInfluxEnabled(Long childId) {
    if (!(settings.influxEnabled as Boolean)) return false
    return settings["influxEnable_${childId}"] as Boolean
}
def installed() { }
def updated() { }
