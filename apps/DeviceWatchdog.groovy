definition(
    name: "Device Watchdog",
    namespace: "local",
    author: "Marc",
    description: "Parent app. Create one watchdog instance per device.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    importUrl: "https://raw.githubusercontent.com/Fernsatron83/Third-Reality-Gen-2-Smart-Plug-Device-Watchdog/main/apps/DeviceWatchdog.groovy"
)
preferences {
    page(name: "mainPage", title: "Device Watchdog", install: true, uninstall: true)
}
def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Watchdog instances") {
            app(
                name: "childApps",
                appName: "Device Watchdog Child",
                namespace: "local",
                title: "Add new watchdog",
                multiple: true
            )
        }
    }
}
def installed() { }
def updated() { }
