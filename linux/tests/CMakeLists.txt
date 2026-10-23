add_executable(ojnexus-core-tests
    core/DomainTest.cpp
)
target_link_libraries(ojnexus-core-tests PRIVATE
    OjNexusCore
    Qt6::Test
)
add_test(NAME ojnexus-core-tests COMMAND ojnexus-core-tests)

add_executable(ojnexus-store-tests
    core/SqliteStoreTest.cpp
)
target_link_libraries(ojnexus-store-tests PRIVATE
    OjNexusCore
    Qt6::Test
)
add_test(NAME ojnexus-store-tests COMMAND ojnexus-store-tests)

add_executable(ojnexus-adapter-tests
    core/AdapterTest.cpp
)
target_link_libraries(ojnexus-adapter-tests PRIVATE
    OjNexusCore
    Qt6::Test
)
add_test(NAME ojnexus-adapter-tests COMMAND ojnexus-adapter-tests)

add_executable(ojnexus-path-tests
    core/PathsTest.cpp
)
target_link_libraries(ojnexus-path-tests PRIVATE
    OjNexusCore
    Qt6::Test
)
add_test(NAME ojnexus-path-tests COMMAND ojnexus-path-tests)

add_executable(ojnexus-sync-engine-tests
    core/SyncEngineTest.cpp
)
target_link_libraries(ojnexus-sync-engine-tests PRIVATE
    OjNexusCore
    Qt6::Test
)
add_test(NAME ojnexus-sync-engine-tests COMMAND ojnexus-sync-engine-tests)

find_program(QMLTEST_RUNNER qmltestrunner HINTS /usr/lib/qt6/bin)
find_path(QTQML_WORKERSCRIPT_MODULE_DIR qmldir
    PATHS /usr/lib/qt6/qml/QtQml/WorkerScript /usr/lib/x86_64-linux-gnu/qt6/qml/QtQml/WorkerScript
    NO_DEFAULT_PATH
)
if(QMLTEST_RUNNER AND QTQML_WORKERSCRIPT_MODULE_DIR)
    add_test(NAME ojnexus-qml-tests COMMAND ${QMLTEST_RUNNER} -input ${CMAKE_CURRENT_SOURCE_DIR}/qml)
else()
    message(STATUS "Skipping QML Test registration: qmltestrunner or QtQml.WorkerScript module is unavailable")
endif()

add_executable(ojnexus-cli-tests
    cli/CommandParserTest.cpp
)
target_link_libraries(ojnexus-cli-tests PRIVATE
    OjNexusCore
    OjNexusCli
    Qt6::Test
)
add_test(NAME ojnexus-cli-tests COMMAND ojnexus-cli-tests)

add_executable(ojnexus-gui-tests
    gui/MainViewModelTest.cpp
    ../src/gui/viewmodel.cpp
    ../src/gui/viewmodel.h
)
target_include_directories(ojnexus-gui-tests PRIVATE
    ${CMAKE_SOURCE_DIR}/src/gui
)
target_link_libraries(ojnexus-gui-tests PRIVATE
    OjNexusCore
    Qt6::Test
    Qt6::Gui
)
add_test(NAME ojnexus-gui-tests COMMAND ojnexus-gui-tests)
