cmake_minimum_required(VERSION 3.28)

project(OJNexusLinux VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

option(OJNEXUS_BUILD_TESTS "Build Linux unit and QML tests" ON)
option(OJNEXUS_BUILD_GUI "Build the Qt Quick desktop client" ON)

find_package(Qt6 6.4 REQUIRED COMPONENTS Core Network Sql)

add_subdirectory(src/core)
add_subdirectory(src/cli)

if(OJNEXUS_BUILD_GUI)
    find_package(Qt6 6.4 REQUIRED COMPONENTS Gui Qml Quick QuickControls2)
    add_subdirectory(src/gui)
endif()

if(OJNEXUS_BUILD_TESTS)
    enable_testing()
    find_package(Qt6 6.4 REQUIRED COMPONENTS Test QuickTest)
    add_subdirectory(tests)
endif()

include(GNUInstallDirs)
install(FILES README.md DESTINATION ${CMAKE_INSTALL_DOCDIR})
install(FILES packaging/ojnexus.desktop DESTINATION ${CMAKE_INSTALL_DATADIR}/applications)

set(CPACK_PACKAGE_NAME "ojnexus")
set(CPACK_PACKAGE_VENDOR "OJ NEXUS")
set(CPACK_PACKAGE_CONTACT "OJ NEXUS maintainers")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Native Qt Linux client and CLI for OJ NEXUS")
set(CPACK_PACKAGE_VERSION ${PROJECT_VERSION})
set(CPACK_GENERATOR "DEB;RPM")
set(CPACK_DEBIAN_PACKAGE_DEPENDS "libqt6core6, libqt6gui6, libqt6network6, libqt6qml6, libqt6quick6, libqt6quickcontrols2-6, libqt6sql6-sqlite")
set(CPACK_RPM_PACKAGE_REQUIRES "qt6-qtbase, qt6-qtdeclarative, qt6-qtquickcontrols2, qt6-qtsql")
include(CPack)
