cmake_minimum_required(VERSION 3.14)
project(worklet2 CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(include)

# Worklet 2 Core Static Library
add_library(worklet2_core STATIC
    src/grammar/Grammar.cpp
    src/grammar/GrammarConfig.cpp
    src/cyk/Cell.cpp
    src/cyk/CYKTable.cpp
    src/tree/TreeBuilder.cpp
    src/tree/TreeChecker.cpp
    src/tree/ActionExtractor.cpp
    src/pipeline/StructuralValidator.cpp
    src/pipeline/Worklet2Gateway.cpp
    src/bridge/Worklet1Adapter.cpp
)

# Demo executable (./worklet2_demo)
add_executable(worklet2_demo src/main.cpp)
target_link_libraries(worklet2_demo PRIVATE worklet2_core)

# Tests
add_executable(test_pipeline tests/test_pipeline.cpp)
target_link_libraries(test_pipeline PRIVATE worklet2_core)

add_executable(bench_cyk tests/bench_cyk.cpp)
target_link_libraries(bench_cyk PRIVATE worklet2_core)
