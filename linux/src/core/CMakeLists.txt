add_library(OjNexusCore STATIC domain.cpp store.cpp storage_schema.cpp http_transport.cpp adapters.cpp paths.cpp sync_engine.cpp portable_archive_store.cpp portable_models.cpp portable_json.cpp portable_codec.cpp)
target_include_directories(OjNexusCore PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(OjNexusCore PUBLIC Qt6::Core Qt6::Sql Qt6::Network)
