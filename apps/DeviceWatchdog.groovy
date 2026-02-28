/*
 * VIBE CODED — USE AT YOUR OWN RISK
 *
 * This code was generated with AI assistance ("vibe coding") and is provided
 * as-is, without warranty of any kind, express or implied. The author accepts
 * no responsibility for any errors, bugs, data loss, device damage, or any
 * other problems that may arise from the use of this code.
 *
 * By importing or using this code you agree that you do so entirely at your
 * own risk.
 */

definition(
    name: "Device Watchdog",
    namespace: "local",
    author: "Marc",
    description: "Parent app. Create one watchdog instance per device.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
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
