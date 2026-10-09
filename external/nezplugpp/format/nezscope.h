#ifndef NEZSCOPE_H__
#define NEZSCOPE_H__

#include "../nestypes.h"

#ifdef __cplusplus
extern "C" {
#endif

enum { NEZ_SCOPE_BLOCK = 256 };

typedef void (*NEZScopeCallback)(int devId, const float *samples, int frames, void *user);
void NEZSetScopeCallback(NEZScopeCallback callback, void *user);
void NEZScopeFlush(void);
void NEZScopeTick(void);
void NEZScopeTap(int devId, Int32 value);

#ifdef __cplusplus
}
#endif
#endif /* NEZSCOPE_H__ */
