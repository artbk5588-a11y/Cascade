/*
 * Android OS timing shim for CASCADE.
 * Derived from the project's os_linux.cpp implementation; CASCADE Public License 1.0.
 */
#include <time.h>
#include <unistd.h>

void os_msleep(int ms)
{
    if (ms <= 0) return;
    usleep(static_cast<useconds_t>(ms) * 1000U);
}

unsigned int os_mtime()
{
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<unsigned int>(ts.tv_sec * 1000U + ts.tv_nsec / 1000000U);
}
