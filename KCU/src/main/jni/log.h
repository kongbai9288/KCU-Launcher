//
// Created by Mio on 2026/8/25.
//

#ifndef FOLD_CRAFT_LAUNCHER_LOG_H
#define FOLD_CRAFT_LAUNCHER_LOG_H

#define KCU_INTERNAL_LOG(x...) do { \
    printf("[KCU Internal] %s:%d\n", __FILE__, __LINE__); \
    printf(x); \
    printf("\n"); \
    } while (0)

#define KCU_LOG(x...) do { \
    printf(x); \
    printf("\n"); \
    } while (0)

#endif //FOLD_CRAFT_LAUNCHER_LOG_H
