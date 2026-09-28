// Double-wide libm shims: float entry points resolve past Debian glibc
// (sqrtf/atan2f/log10f 2.43, cosh/sinh 2.44); the double forms are ancient.
#include <math.h>

float __wrap_sqrtf(float x) { return (float)sqrt((double)x); }
float __wrap_atan2f(float y, float x) { return (float)atan2((double)y, (double)x); }
float __wrap_log10f(float x) { return (float)log10((double)x); }
double __wrap_cosh(double x) { double e = exp(x); return 0.5 * (e + 1.0 / e); }
double __wrap_sinh(double x) { double e = exp(x); return 0.5 * (e - 1.0 / e); }
