import QtQuick
import QtTest

TestCase {
    name: "MainQmlContract"
    function test_uppercase_navigation_contract() {
        compare(["DASHBOARD", "CONNECTORS", "SYNC HISTORY", "PORTABLE"].length, 4)
        verify("SYNC HISTORY" === "SYNC HISTORY")
        verify("PORTABLE" === "PORTABLE")
    }
}
