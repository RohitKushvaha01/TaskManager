cmake_minimum_required(VERSION 3.22.1)
project("taskmanagerd")

add_executable(${CMAKE_PROJECT_NAME}
        taskmanagerd.cpp)

set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
        OUTPUT_NAME "taskmanagerd"
        PREFIX "lib"
        SUFFIX ".so"
)


target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=."
)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,--build-id=none"
        "-Wl,--threads=1"
)

# Strip as part of the build with the pinned NDK's own llvm-strip, using the exact invocation
# (--strip-unneeded -o) that AGP's stripReleaseDebugSymbols task uses. Linking with plain "-s"
# does not remove everything AGP's strip pass does (it leaves the stale section-name table), so
# the packaged library used to depend on whether that later AGP task found its strip tool or fell
# back to "Unable to strip ... packaging them as they are". With this the artifact is already in
# AGP's final form and re-stripping it is a no-op, so the output no longer depends on the host.
#
# Note: the toolchain identity stays in the ELF (.comment holds the clang/LLD version,
# .note.android.ident holds the NDK release string). That is deliberate -- rewriting those bytes
# affects data the linker and crash tooling read. The pin on `ndk` in gradle/libs.versions.toml is
# what keeps the output stable across machines, not scrubbing the ELF.
if(CMAKE_STRIP)
    add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
            COMMAND ${CMAKE_STRIP} --strip-unneeded -o $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
                    $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
            COMMENT "Stripping ${CMAKE_PROJECT_NAME} with ${CMAKE_STRIP}"
            VERBATIM
    )
endif()
