cmake_minimum_required(VERSION 3.3)
project(questdb)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -m64 -Wimplicit-function-declaration")
#set(CMAKE_VERBOSE_MAKEFILE  on)

# deal with windows slashes in JAVA_HOME
if ($ENV{JAVA_HOME})
    FILE(TO_CMAKE_PATH $ENV{JAVA_HOME} JAVA_HOME)
endif ($ENV{JAVA_HOME})

set(SOURCE_FILES
        src/main/c/share/files.h
        src/main/c/share/zip.h
        src/main/c/share/net.h
        src/main/c/share/zip.c
        src/main/c/share/os.h)

# JNI includes
include_directories(${JAVA_HOME}/include/)

if (APPLE)
    include_directories(${JAVA_HOME}/include/darwin/)
    set(OUTPUT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources/binaries/osx)
    set(SOURCE_FILES ${SOURCE_FILES}
            src/main/c/osx/kqueue.h
            src/main/c/osx/kqueue.c
            src/main/c/share/net.c
            )
elseif (UNIX)
    include_directories(${JAVA_HOME}/include/linux/)
    set(OUTPUT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources/binaries/linux)
    set(SOURCE_FILES ${SOURCE_FILES}
            src/main/c/share/net.c
            src/main/c/linux/epoll.h src/main/c/linux/epoll.c)
endif (APPLE)

if (WIN32)
    include_directories(${JAVA_HOME}/include/win32/)
    set(OUTPUT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/resources/binaries/windows)
    set(SOURCE_FILES ${SOURCE_FILES}
            src/main/c/windows/files.c
            src/main/c/windows/os.c
            src/main/c/windows/net.c
            src/main/c/windows/select.h
            src/main/c/windows/select.c)
else ()
    set(SOURCE_FILES
            ${SOURCE_FILES}
            src/main/c/share/files.c
            src/main/c/share/os.c)
endif (WIN32)

set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${OUTPUT})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${OUTPUT})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${OUTPUT})

#zlib
set(ZLIB_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src/main/c/share/zlib-1.2.8)
include(${ZLIB_SOURCE_DIR}/CMakeLists.txt)

add_library(questdb SHARED ${SOURCE_FILES} ${ZLIB_SRCS})

if (WIN32)
    target_link_libraries(questdb wsock32 ws2_32)
endif (WIN32)
