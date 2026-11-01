add_executable(ojnexus-gui
    main.cpp
    viewmodel.cpp
    viewmodel.h
    qml.qrc
)
target_link_libraries(ojnexus-gui PRIVATE
    OjNexusCore
    Qt6::Core
    Qt6::Gui
    Qt6::Qml
    Qt6::Quick
    Qt6::QuickControls2
)
install(TARGETS ojnexus-gui RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
