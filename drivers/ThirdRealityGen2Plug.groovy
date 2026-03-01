import hubitat.zigbee.zcl.DataType

metadata {
    definition(name: "Third Reality Gen2 Plug (Custom Driver)", namespace: "local", author: "Marc",
               importUrl: "https://raw.githubusercontent.com/Fernsatron83/Third-Reality-Gen-2-Smart-Plug-Device-Watchdog/main/drivers/ThirdRealityGen2Plug.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Refresh"
        capability "Configuration"

        capability "PowerMeter"
        capability "VoltageMeasurement"

        attribute "amperage", "number"
        attribute "frequency", "number"
        attribute "powerFactor", "number"
        attribute "lastSeen", "string"

        capability "EnergyMeter"
        attribute "energyScalingStatus", "string"   // unknown | ready | unsupported
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable description logging", defaultValue: true

        input name: "enableReportingConfig", type: "bool", title: "Attempt Zigbee reporting configuration", defaultValue: true
        input name: "allowEnergyGuess", type: "bool", title: "If metering scaling unsupported, publish energy guess (kWh)", defaultValue: false

        input name: "enableDriverPolling", type: "bool", title: "Enable driver-scheduled polling (normally OFF; app should own polling)", defaultValue: false
        input name: "driverPollSeconds", type: "number", title: "Driver poll interval (seconds)", defaultValue: 60

        input name: "startUpOnOff", type: "enum", title: "Power-on behavior after power outage (applied on Configure)",
              options: ["on": "Turn ON", "off": "Turn OFF", "previous": "Restore previous state", "none": "Do not set"],
              defaultValue: "none"
    }
}

def installed() {
    initializeState()
    if (logEnable) log.info "${device.displayName} installed"
    if (logEnable) runIn(1800, "logsOff")
}

def updated() {
    initializeState()
    if (logEnable) log.info "${device.displayName} updated"
    if (logEnable) runIn(1800, "logsOff")
    unschedule("driverPollTick")
    scheduleDriverPollIfEnabled()
}

private void initializeState() {
    if (!(state.emMultipliers instanceof Map)) state.emMultipliers = [:]
    if (!(state.emDivisors instanceof Map))    state.emDivisors = [:]

    if (state.meteringMultiplier != null && !(state.meteringMultiplier instanceof BigInteger)) state.remove("meteringMultiplier")
    if (state.meteringDivisor != null && !(state.meteringDivisor instanceof BigInteger)) state.remove("meteringDivisor")

    if (state.energyScalingStatus == null) state.energyScalingStatus = "unknown"
    if (state.energyScaleMissCount == null) state.energyScaleMissCount = 0
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "Debug logging disabled for ${device.displayName}"
}

def parse(String description) {
    initializeState()

    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (!descMap) return

    updateLastSeen()

    if (logEnable) log.debug "descMap: ${descMap}"

    if (!descMap.cluster || !descMap.attrId || descMap.value == null) return

    String cluster = descMap.cluster.toString().toUpperCase()
    String attrId  = descMap.attrId.toString().toUpperCase()
    String rawHex  = descMap.value.toString()

    processAttr(cluster, attrId, rawHex)

    if (descMap.additionalAttrs instanceof List) {
        descMap.additionalAttrs.each { a ->
            try {
                String aAttrId = a.attrId?.toString()?.toUpperCase()
                String aVal    = a.value?.toString()
                if (aAttrId && aVal != null) processAttr(cluster, aAttrId, aVal)
            } catch (e) {
                if (logEnable) log.debug "Failed processing additionalAttrs: ${e}"
            }
        }
    }
}

private void updateLastSeen() {
    String ts = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    sendEvent(name: "lastSeen", value: ts, isStateChange: true)
}

private void processAttr(String cluster, String attrId, String rawHex) {
    if (cluster == "0006" && attrId == "0000") {
        BigInteger v = hexToBigInt(rawHex)
        if (v != null) {
            String s = (v.intValue() == 1) ? "on" : "off"
            sendEvent(name: "switch", value: s)
            if (txtEnable) log.info "${device.displayName} switch is ${s}"
        }
        return
    }

    if (cluster == "0B04") {
        captureEMScaling(attrId, rawHex)
        decodeEM(attrId, rawHex)
        return
    }

    if (cluster == "0702") {
        captureMeteringScaling(attrId, rawHex)
        decodeMetering(attrId, rawHex)
        return
    }
}

private void captureEMScaling(String attrId, String rawHex) {
    BigInteger v = hexToBigInt(rawHex)
    if (v == null) return

    switch (attrId) {
        case "0600": state.emMultipliers["voltage"] = v; if (logEnable) log.debug "EM voltage multiplier=${v}"; break
        case "0601": state.emDivisors["voltage"]    = v; if (logEnable) log.debug "EM voltage divisor=${v}"; break
        case "0602": state.emMultipliers["current"] = v; if (logEnable) log.debug "EM current multiplier=${v}"; break
        case "0603": state.emDivisors["current"]    = v; if (logEnable) log.debug "EM current divisor=${v}"; break
        case "0604": state.emMultipliers["power"]   = v; if (logEnable) log.debug "EM power multiplier=${v}"; break
        case "0605": state.emDivisors["power"]      = v; if (logEnable) log.debug "EM power divisor=${v}"; break
    }
}

private void decodeEM(String attrId, String rawHex) {
    BigInteger v = hexToBigInt(rawHex)
    if (v == null) return

    switch (attrId) {
        case "050B":
            BigDecimal watts = applyEMScale(v, "power")
            sendEvent(name: "power", value: safeNum(watts), unit: "W")
            if (logEnable) log.debug "Power ${safeNum(watts)} W (raw ${v})"
            return

        case "0505":
            BigDecimal volts = applyEMScale(v, "voltage")
            sendEvent(name: "voltage", value: safeNum(volts), unit: "V")
            if (logEnable) log.debug "Voltage ${safeNum(volts)} V (raw ${v})"
            return

        case "0508":
            BigDecimal amps = applyEMScale(v, "current")
            sendEvent(name: "amperage", value: safeNum(amps), unit: "A")
            if (logEnable) log.debug "Current ${safeNum(amps)} A (raw ${v})"
            return

        case "0510":
            BigDecimal pf = guessPowerFactor(v)
            if (pf < 0) pf = 0
            if (pf > 1) pf = 1
            sendEvent(name: "powerFactor", value: safeNum(pf))
            if (logEnable) log.debug "PF ${safeNum(pf)} (raw ${v})"
            return

        case "0300":
            BigDecimal hz = new BigDecimal(v.intValue())
            sendEvent(name: "frequency", value: safeNum(hz), unit: "Hz")
            if (logEnable) log.debug "Frequency ${safeNum(hz)} Hz (raw ${v})"
            return
    }
}

private void captureMeteringScaling(String attrId, String rawHex) {
    BigInteger v = hexToBigInt(rawHex)
    if (v == null) return

    if (attrId == "0300") {
        state.meteringMultiplier = v
        if (logEnable) log.debug "Metering multiplier=${v}"
        updateEnergyScalingStatus()
        return
    }
    if (attrId == "0301") {
        state.meteringDivisor = v
        if (logEnable) log.debug "Metering divisor=${v}"
        updateEnergyScalingStatus()
        return
    }
}

private void updateEnergyScalingStatus() {
    if (state.meteringMultiplier instanceof BigInteger && state.meteringDivisor instanceof BigInteger && (state.meteringDivisor as BigInteger) != 0) {
        state.energyScalingStatus = "ready"
        state.energyScaleMissCount = 0
        sendEvent(name: "energyScalingStatus", value: "ready", isStateChange: true)
    }
}

private void maybeMarkEnergyUnsupported() {
    if (state.energyScalingStatus == "ready") return

    state.energyScaleMissCount = (state.energyScaleMissCount ?: 0) + 1
    if (logEnable) {
        log.debug "Energy scaling not ready. missCount=${state.energyScaleMissCount} mult=${state.meteringMultiplier} div=${state.meteringDivisor}"
    }

    if ((state.energyScaleMissCount as Integer) >= 3) {
        state.energyScalingStatus = "unsupported"
        sendEvent(name: "energyScalingStatus", value: "unsupported", isStateChange: true)
        if (logEnable) log.debug "Marked energyScalingStatus=unsupported after repeated misses"
    } else {
        sendEvent(name: "energyScalingStatus", value: "unknown", isStateChange: true)
    }
}

private void decodeMetering(String attrId, String rawHex) {
    BigInteger v = hexToBigInt(rawHex)
    if (v == null) return

    if (attrId == "0000") {
        BigDecimal kwh = null

        if (state.energyScalingStatus == "ready") {
            BigDecimal mult = new BigDecimal((state.meteringMultiplier as BigInteger).toString())
            BigDecimal div  = new BigDecimal((state.meteringDivisor as BigInteger).toString())
            BigDecimal raw  = new BigDecimal(v.toString())

            BigDecimal wh = raw.multiply(mult).divide(div, 3, BigDecimal.ROUND_HALF_UP)
            kwh = wh.divide(new BigDecimal("1000"), 3, BigDecimal.ROUND_HALF_UP)
        } else if (settings.allowEnergyGuess) {
            kwh = new BigDecimal(v.toString()).divide(new BigDecimal("1000"), 3, BigDecimal.ROUND_HALF_UP)
        } else {
            return
        }

        sendEvent(name: "energy", value: safeNum(kwh), unit: "kWh")
        if (logEnable) log.debug "Energy ${safeNum(kwh)} kWh (raw ${v})"
        return
    }
}

def on() {
    if (txtEnable) log.info "${device.displayName} on()"
    return zigbee.on()
}

def off() {
    if (txtEnable) log.info "${device.displayName} off()"
    return zigbee.off()
}

def refresh() {
    if (txtEnable) log.info "${device.displayName} refresh()"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000)

    cmds += zigbee.readAttribute(0x0B04, 0x0600)
    cmds += zigbee.readAttribute(0x0B04, 0x0601)
    cmds += zigbee.readAttribute(0x0B04, 0x0602)
    cmds += zigbee.readAttribute(0x0B04, 0x0603)
    cmds += zigbee.readAttribute(0x0B04, 0x0604)
    cmds += zigbee.readAttribute(0x0B04, 0x0605)

    cmds += zigbee.readAttribute(0x0B04, 0x0300)
    cmds += zigbee.readAttribute(0x0B04, 0x0505)
    cmds += zigbee.readAttribute(0x0B04, 0x0508)
    cmds += zigbee.readAttribute(0x0B04, 0x050B)
    cmds += zigbee.readAttribute(0x0B04, 0x0510)

    cmds += zigbee.readAttribute(0x0702, 0x0300)
    cmds += zigbee.readAttribute(0x0702, 0x0301)
    cmds += zigbee.readAttribute(0x0702, 0x0000)

    // After refresh, if scaling hasn't appeared after a few tries, mark unsupported
    runIn(2, "postRefreshHousekeeping")

    return cmds
}

def postRefreshHousekeeping() {
    // If device never returns multiplier/divisor, we stop pretending it's "unknown" forever
    if (state.energyScalingStatus != "ready" && !(state.meteringMultiplier instanceof BigInteger) && !(state.meteringDivisor instanceof BigInteger)) {
        maybeMarkEnergyUnsupported()
    }
}

private List<String> startUpOnOffCmds() {
    String pref = (settings.startUpOnOff as String) ?: "none"
    Integer val
    switch (pref) {
        case "on":       val = 0x01; break
        case "off":      val = 0x00; break
        case "previous": val = 0xFF; break
        default:         return []
    }
    try {
        List<String> cmds = zigbee.writeAttribute(0x0006, 0x4003, DataType.ENUM8, val)
        if (logEnable) log.debug "${device.displayName} StartUpOnOff set to ${pref} (0x${Integer.toHexString(val).toUpperCase()})"
        return cmds
    } catch (e) {
        log.warn "${device.displayName} StartUpOnOff write failed: ${e}"
        return []
    }
}

def configure() {
    if (txtEnable) log.info "${device.displayName} configure()"
    List<String> cmds = []
    cmds += zigbee.onOffConfig()
    cmds += startUpOnOffCmds()

    if (settings.enableReportingConfig) {
        try { cmds += zigbee.configureReporting(0x0B04, 0x050B, DataType.INT16,  5, 300, 1) } catch (e) { if (logEnable) log.debug "Reporting config power failed: ${e}" }
        try { cmds += zigbee.configureReporting(0x0B04, 0x0508, DataType.UINT16, 5, 300, 1) } catch (e) { if (logEnable) log.debug "Reporting config current failed: ${e}" }
        try { cmds += zigbee.configureReporting(0x0B04, 0x0300, DataType.UINT16, 5, 300, 1) } catch (e) { if (logEnable) log.debug "Reporting config frequency failed: ${e}" }
        try { cmds += zigbee.configureReporting(0x0702, 0x0000, DataType.UINT48, 60, 600, 1) } catch (e) { if (logEnable) log.debug "Reporting config energy failed: ${e}" }
    }

    scheduleDriverPollIfEnabled()
    cmds += refresh()
    return cmds
}

private void scheduleDriverPollIfEnabled() {
    if (!settings.enableDriverPolling) return

    Integer s = (settings.driverPollSeconds ?: 60) as Integer
    if (s < 15) s = 15
    if (s > 3600) s = 3600

    unschedule("driverPollTick")

    // Hubitat scheduling works best with cron style. We'll map to the nearest minute for >60.
    if (s <= 60) {
        // Every N seconds is not supported by cron. We'll use runIn recursion.
        if (logEnable) log.debug "Driver polling enabled via runIn recursion every ${s}s"
        runIn(s, "driverPollTick")
    } else {
        Integer mins = Math.max(1, Math.round(s / 60.0) as Integer)
        if (logEnable) log.debug "Driver polling enabled via cron every ${mins} minute(s)"
        schedule("0 */${mins} * ? * * *", "driverPollTick")
    }
}

def driverPollTick() {
    if (!settings.enableDriverPolling) return
    refresh()
    Integer s = (settings.driverPollSeconds ?: 60) as Integer
    if (s < 15) s = 15
    if (s <= 60) runIn(s, "driverPollTick")
}

private BigInteger hexToBigInt(String hex) {
    if (hex == null) return null
    try { return new BigInteger(hex, 16) } catch (e) { return null }
}

private BigDecimal applyEMScale(BigInteger raw, String kind) {
    BigInteger m = (state.emMultipliers instanceof Map) ? (state.emMultipliers[kind] as BigInteger) : null
    BigInteger d = (state.emDivisors    instanceof Map) ? (state.emDivisors[kind]    as BigInteger) : null

    BigDecimal v = new BigDecimal(raw.toString())
    BigDecimal mm = new BigDecimal((m ?: 1).toString())
    BigDecimal dd = new BigDecimal((d ?: 1).toString())
    if (dd.compareTo(BigDecimal.ZERO) == 0) dd = BigDecimal.ONE

    return v.multiply(mm).divide(dd, 3, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal guessPowerFactor(BigInteger raw) {
    int r = raw.intValue()
    if (r <= 100)  return new BigDecimal(r).divide(new BigDecimal("100"), 3, BigDecimal.ROUND_HALF_UP)
    if (r <= 1000) return new BigDecimal(r).divide(new BigDecimal("1000"), 3, BigDecimal.ROUND_HALF_UP)
    return new BigDecimal(r)
}

private String safeNum(BigDecimal v) {
    if (v == null) return null
    return v.stripTrailingZeros().toPlainString()
}
