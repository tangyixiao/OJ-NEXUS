import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window

ApplicationWindow {
    visible: true
    width: 1180
    height: 760
    minimumWidth: 900
    minimumHeight: 620
    title: "OJ NEXUS / LINUX"
    color: "#0B1018"

    palette.window: "#0B1018"
    palette.windowText: "#E6EDF7"
    palette.button: "#162236"
    palette.buttonText: "#E6EDF7"
    palette.highlight: "#2F81F7"
    palette.highlightedText: "#FFFFFF"

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 28
        spacing: 20

        RowLayout {
            Layout.fillWidth: true
            Text { text: "OJ NEXUS"; color: "#E6EDF7"; font.pixelSize: 28; font.bold: true }
            Text { text: "LINUX CLIENT / CONNECTION CENTER"; color: "#7E8CA3"; font.pixelSize: 12; Layout.alignment: Qt.AlignBottom; Layout.bottomMargin: 4 }
            Item { Layout.fillWidth: true }
            Text { text: viewModel.statusLine; color: viewModel.busy ? "#F2CC60" : "#7E8CA3"; font.pixelSize: 12 }
        }

        TabBar {
            id: tabs
            Layout.fillWidth: true
            onCurrentIndexChanged: viewModel.currentPage = ["DASHBOARD", "CONNECTORS", "SYNC HISTORY"][currentIndex]
            TabButton { text: "DASHBOARD" }
            TabButton { text: "CONNECTORS" }
            TabButton { text: "SYNC HISTORY" }
        }

        StackLayout {
            currentIndex: tabs.currentIndex
            Layout.fillWidth: true
            Layout.fillHeight: true

            Pane {
                background: Rectangle { color: "#111927"; border.color: "#27364A"; radius: 8 }
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 28; spacing: 18
                    Text { text: "LOCAL OPERATIONS"; color: "#7E8CA3"; font.pixelSize: 12 }
                    Text { text: viewModel.accountSummary; color: "#E6EDF7"; font.pixelSize: 34; font.bold: true }
                    Text { text: "LAST PUBLIC PROFILE"; color: "#7E8CA3"; font.pixelSize: 12 }
                    Text { text: viewModel.profileSummary; color: "#2F81F7"; font.pixelSize: 18; font.bold: true }
                    Text { text: "SQLite ledger is the source of truth. Network sync remains cancellable and offline-safe."; color: "#A9B5C7"; wrapMode: Text.WordWrap; Layout.maximumWidth: 620 }
                    RowLayout {
                        spacing: 12
                        Button { text: "SYNC ALL"; enabled: !viewModel.busy; onClicked: viewModel.startSync("all") }
                        Button { text: "CANCEL"; enabled: viewModel.busy; onClicked: viewModel.cancel() }
                        Button { text: "REFRESH"; onClicked: viewModel.refresh() }
                    }
                    Item { Layout.fillHeight: true }
                }
            }

            Pane {
                background: Rectangle { color: "#111927"; border.color: "#27364A"; radius: 8 }
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 28; spacing: 14
                    Text { text: "CONNECTOR CAPABILITIES"; color: "#7E8CA3"; font.pixelSize: 12 }
                    RowLayout {
                        Layout.fillWidth: true; spacing: 10
                        ComboBox {
                            id: judgeSelector
                            model: ["CODEFORCES", "ATCODER", "LUOGU"]
                            Layout.preferredWidth: 170
                        }
                        TextField {
                            id: handleField
                            placeholderText: "PUBLIC HANDLE / UID"
                            Layout.fillWidth: true
                            enabled: !viewModel.busy
                            onAccepted: viewModel.configureAccount(judgeSelector.currentText, text)
                        }
                        Button {
                            text: "SAVE ACCOUNT"
                            enabled: !viewModel.busy && handleField.text.trim().length > 0
                            onClicked: {
                                viewModel.configureAccount(judgeSelector.currentText, handleField.text)
                                handleField.clear()
                            }
                        }
                    }
                    Text { text: "PUBLIC SYNC / NO CREDENTIALS"; color: "#7E8CA3"; font.pixelSize: 11 }
                    Repeater {
                        model: viewModel.configuredAccounts
                        delegate: Rectangle {
                            property var recentSubmissionRows: modelData.recentSubmissions
                            Layout.fillWidth: true; height: 126 + recentSubmissionRows.length * 18; color: "#0B1018"; border.color: "#27364A"; radius: 4
                            ColumnLayout { anchors.fill: parent; anchors.margins: 10; spacing: 4
                                RowLayout { Layout.fillWidth: true; spacing: 14
                                    Text { text: modelData.judge; color: "#2F81F7"; font.bold: true; Layout.preferredWidth: 120 }
                                    Text { text: modelData.handle; color: "#E6EDF7"; Layout.fillWidth: true }
                                    Text { text: modelData.enabled ? "ENABLED" : "DISABLED"; color: modelData.enabled ? "#68D391" : "#F2CC60"; Layout.preferredWidth: 82 }
                                    Button { text: modelData.enabled ? "DISABLE" : "ENABLE"; enabled: !viewModel.busy; onClicked: viewModel.setAccountEnabled(modelData.judge, !modelData.enabled) }
                                    Button { text: "SYNC"; enabled: !viewModel.busy && modelData.enabled; onClicked: viewModel.startSync(modelData.judge) }
                                    Button { text: "REMOVE"; enabled: !viewModel.busy; onClicked: viewModel.removeAccount(modelData.judge) }
                                }
                                Text { text: modelData.profileSummary; color: "#7E8CA3"; font.pixelSize: 11; elide: Text.ElideRight; Layout.fillWidth: true }
                                Text { text: modelData.ratingSummary; color: "#7E8CA3"; font.pixelSize: 11; elide: Text.ElideRight; Layout.fillWidth: true }
                                Text { text: modelData.submissionSummary; color: "#7E8CA3"; font.pixelSize: 11; elide: Text.ElideRight; Layout.fillWidth: true }
                                Column {
                                    Layout.fillWidth: true
                                    Repeater {
                                        model: recentSubmissionRows
                                        delegate: Text {
                                            text: modelData.verdict + " / " + (modelData.problemId === "" ? "UNKNOWN PROBLEM" : modelData.problemId)
                                                + (modelData.submittedAt === "" ? " / TIME UNKNOWN" : " / " + modelData.submittedAt)
                                                + (modelData.language === "" ? "" : " / " + modelData.language)
                                            color: "#A9B5C7"; font.pixelSize: 10; elide: Text.ElideRight; width: parent.width
                                        }
                                    }
                                }
                                Text { text: modelData.lastSyncSummary; color: "#A9B5C7"; font.pixelSize: 11; elide: Text.ElideRight; Layout.fillWidth: true }
                                Text { text: modelData.lastSyncFinishedAt === "" ? "" : "FINISHED " + modelData.lastSyncFinishedAt; color: "#7E8CA3"; font.pixelSize: 10; elide: Text.ElideRight; Layout.fillWidth: true }
                            }
                        }
                    }
                    Repeater {
                        model: viewModel.capabilityRows
                        delegate: Rectangle {
                            Layout.fillWidth: true; height: 68; color: "#0B1018"; border.color: "#27364A"; radius: 4
                            Column { anchors.fill: parent; anchors.margins: 14; spacing: 6
                                Text { text: modelData.judge; color: "#E6EDF7"; font.bold: true }
                                Text { text: modelData.summary; color: "#7E8CA3"; font.pixelSize: 11; wrapMode: Text.WordWrap }
                            }
                        }
                    }
                    Item { Layout.fillHeight: true }
                }
            }

            Pane {
                background: Rectangle { color: "#111927"; border.color: "#27364A"; radius: 8 }
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 28; spacing: 12
                    Text { text: "SYNC HISTORY / LAST 40"; color: "#7E8CA3"; font.pixelSize: 12 }
                    ComboBox {
                        model: ["ALL", "CODEFORCES", "ATCODER", "LUOGU"]
                        Layout.preferredWidth: 180
                        currentIndex: viewModel.historyFilter === "all" ? 0 : model.indexOf(viewModel.historyFilter.toUpperCase())
                        onActivated: viewModel.setHistoryFilter(currentText)
                    }
                    ListView {
                        Layout.fillWidth: true; Layout.fillHeight: true; clip: true; model: viewModel.history; spacing: 6
                        delegate: Rectangle {
                            width: ListView.view.width; height: 54; color: "#0B1018"; border.color: "#27364A"; radius: 4
                            RowLayout { anchors.fill: parent; anchors.margins: 12; spacing: 18
                                Text { text: judge; color: "#2F81F7"; font.bold: true; Layout.preferredWidth: 120 }
                                Text { text: handle; color: "#E6EDF7"; Layout.preferredWidth: 180 }
                                Text { text: error === "" ? status : status + " / " + error; color: status === "SUCCESS" ? "#68D391" : "#F2CC60"; Layout.preferredWidth: 170 }
                                Text { text: moduleCount + " MODULES"; color: "#7E8CA3"; Layout.preferredWidth: 110 }
                                Text { text: moduleSummary; color: "#A9B5C7"; elide: Text.ElideRight; Layout.preferredWidth: 270 }
                                Text { text: startedAt; color: "#7E8CA3"; Layout.fillWidth: true }
                                Button { text: "RETRY"; visible: status !== "SUCCESS"; enabled: !viewModel.busy; onClicked: viewModel.retryHistory(operationId) }
                            }
                        }
                        Text { anchors.centerIn: parent; visible: parent.count === 0; text: "EMPTY / NO SYNC OPERATIONS"; color: "#7E8CA3" }
                    }
                }
            }
        }
    }
}
