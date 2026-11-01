add_library(OjNexusCli STATIC command_parser.cpp runner.cpp)
target_include_directories(OjNexusCli PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(OjNexusCli PUBLIC OjNexusCore Qt6::Core)

add_executable(ojnexus main.cpp)
target_link_libraries(ojnexus PRIVATE OjNexusCli Qt6::Core)
install(TARGETS ojnexus RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
