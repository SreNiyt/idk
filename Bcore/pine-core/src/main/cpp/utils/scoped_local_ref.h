#ifndef PINE_UTILS_SCOPED_LOCAL_REF_H
#define PINE_UTILS_SCOPED_LOCAL_REF_H

#include <jni.h>
#include <cstddef>

template<typename T>
class ScopedLocalRef {
public:
    ScopedLocalRef(JNIEnv* env) : env_(env), ref_(nullptr) {}
    ScopedLocalRef(JNIEnv* env, T ref) : env_(env), ref_(ref) {}
    ~ScopedLocalRef() { if (ref_ != nullptr) env_->DeleteLocalRef(ref_); }
    
    T get() const { return ref_; }
    T Get() const { return ref_; }
    bool IsNull() const { return ref_ == nullptr; }
    
    void reset(T ptr = nullptr) {
        if (ptr != ref_) {
            if (ref_ != nullptr) env_->DeleteLocalRef(ref_);
            ref_ = ptr;
        }
    }
    
    bool operator==(std::nullptr_t) const { return ref_ == nullptr; }
    bool operator!=(std::nullptr_t) const { return ref_ != nullptr; }

protected:
    JNIEnv* const env_;
    T ref_;

private:
    ScopedLocalRef(const ScopedLocalRef&) = delete;
    void operator=(const ScopedLocalRef&) = delete;
};

class ScopedLocalClassRef : public ScopedLocalRef<jclass> {
public:
    ScopedLocalClassRef(JNIEnv* env) : ScopedLocalRef<jclass>(env) {}
    ScopedLocalClassRef(JNIEnv* env, jclass ref) : ScopedLocalRef<jclass>(env, ref) {}
    // The missing string constructor:
    ScopedLocalClassRef(JNIEnv* env, const char* name) : ScopedLocalRef<jclass>(env, env->FindClass(name)) {}
};

class ScopedLocalObjectRef : public ScopedLocalRef<jobject> {
public:
    ScopedLocalObjectRef(JNIEnv* env) : ScopedLocalRef<jobject>(env) {}
    ScopedLocalObjectRef(JNIEnv* env, jobject ref) : ScopedLocalRef<jobject>(env, ref) {}
};

class ScopedLocalUtfStringRef : public ScopedLocalRef<jstring> {
public:
    ScopedLocalUtfStringRef(JNIEnv* env) : ScopedLocalRef<jstring>(env) {}
    ScopedLocalUtfStringRef(JNIEnv* env, jstring ref) : ScopedLocalRef<jstring>(env, ref) {}
};

#endif //PINE_UTILS_SCOPED_LOCAL_REF_H
