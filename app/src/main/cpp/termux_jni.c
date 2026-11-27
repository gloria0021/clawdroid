/*
 * Termux の com.termux.terminal.JNI の代わり。
 *
 * Termux の AAR（v0.118.3）に入っている libtermux.so は LOAD セグメントが 4KB 境界のため、
 * 16KB ページの端末で互換性の警告が出る。SumahoClaw は端末内でシェルを起動せず、Gateway の PTY に
 * つなぐ（RemoteTerminalBridge）ので、PTY を扱うネイティブの処理は使わない。それでも TerminalSession は
 * 大きさが変わるたびに JNI.setPtyWindowSize を呼ぶため、同じ名前のライブラリを 16KB 境界で作り、
 * 何もしない関数で置き換える。大きさは Gateway へ別に伝えている。
 */
#include <jni.h>

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_createSubprocess(
    JNIEnv *env, jclass clazz, jstring cmd, jstring cwd, jobjectArray args, jobjectArray env_vars,
    jintArray process_id, jint rows, jint columns, jint cell_width, jint cell_height) {
    (void) clazz; (void) cmd; (void) cwd; (void) args; (void) env_vars; (void) process_id;
    (void) rows; (void) columns; (void) cell_width; (void) cell_height;
    jclass error = (*env)->FindClass(env, "java/lang/UnsupportedOperationException");
    if (error != NULL) {
        (*env)->ThrowNew(env, error, "SumahoClaw does not start local processes");
    }
    return -1;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_setPtyWindowSize(
    JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols, jint cell_width, jint cell_height) {
    (void) env; (void) clazz; (void) fd; (void) rows; (void) cols; (void) cell_width; (void) cell_height;
}

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env; (void) clazz; (void) pid;
    return 0;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_close(JNIEnv *env, jclass clazz, jint fd) {
    (void) env; (void) clazz; (void) fd;
}
