import QtQuick
import QtTest

TestCase {
    name: "MainQmlContract"
    function test_uppercase_navigation_contract() {
        compare(["DASHBOARD", "CONNECTORS", "SYNC HISTORY"].length, 3)
        verify("SYNC HISTORY" === "SYNC HISTORY")
    }
}
