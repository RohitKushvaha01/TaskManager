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
        "-s"
)