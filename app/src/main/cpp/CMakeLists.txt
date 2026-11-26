cmake_minimum_required(VERSION 3.22.1)
project("dwgbridge")

add_library(dwgbridge SHARED dwg_jni.cpp)

# Nếu đã biên dịch GNU LibreDWG cho Android NDK và đặt trong third_party/libredwg/<ABI>/
set(LIBREDWG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libredwg/${ANDROID_ABI}")
if(EXISTS "${LIBREDWG_DIR}/libredwg.a")
    message(STATUS "Linking GNU LibreDWG static library from ${LIBREDWG_DIR}")
    target_compile_definitions(dwgbridge PRIVATE HAVE_LIBREDWG=1)
    target_include_directories(dwgbridge PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libredwg/include")
    target_link_libraries(dwgbridge "${LIBREDWG_DIR}/libredwg.a")
else()
    message(STATUS "LibreDWG not found for ${ANDROID_ABI} - building bridge in header/fallback mode")
endif()

find_library(log-lib log)
target_link_libraries(dwgbridge ${log-lib})
