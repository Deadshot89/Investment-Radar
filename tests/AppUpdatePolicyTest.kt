package de.tobias.investmentradar

fun main() {
    check(UpdatePolicy.isNewer(installedCode = 12, latestCode = 13))
    check(!UpdatePolicy.isNewer(installedCode = 13, latestCode = 13))
    check(!UpdatePolicy.isNewer(installedCode = 14, latestCode = 13))
    check(UpdatePolicy.displayVersion("1.2.2") == "1.2.2")
    check(UpdatePolicy.displayVersion(" v1.2.3 ") == "1.2.3")
    println("AppUpdatePolicy tests passed")
}
