package com.alibaba.ttl.threadpool.agent.internal.transformlet.impl;

import com.alibaba.ttl.threadpool.agent.internal.logging.Logger;
import com.alibaba.ttl.threadpool.agent.internal.transformlet.JavassistTransformlet;
import javassist.*;

import java.io.IOException;
import java.lang.reflect.Modifier;

import static com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.*;

/**
 * TTL {@link JavassistTransformlet} for {@link java.util.TimerTask}.
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @author wuwen5 (wuwen.55 at aliyun dot com)
 * @see java.util.TimerTask
 * @see java.util.Timer
 * @since 2.7.0
 */
public class TtlFutureTaskTransformlet implements JavassistTransformlet {
    private static final Logger logger = Logger.getLogger(TtlFutureTaskTransformlet.class);

    @Override
    public byte[] doTransform(String className, byte[] classFileBuffer, ClassLoader loader) throws IOException, NotFoundException, CannotCompileException {
        try {

            if (className.equals("java.util.concurrent.FutureTask")) {
                final CtClass clazz = getCtClass(classFileBuffer, loader);

                // 添加变量
                CtField f = CtField.make("private java.util.concurrent.atomic.AtomicReference capturedRef;", clazz);
                clazz.addField(f);
                CtField f2 = CtField.make("private Object backup;", clazz);
                clazz.addField(f2);


                // 更新 构造方法
                CtConstructor[] constructMethods = clazz.getConstructors();
                for(CtConstructor ctMethod: constructMethods) {
                    String code = "capturedRef = new java.util.concurrent.atomic.AtomicReference(com.alibaba.ttl.TransmittableThreadLocal.Transmitter.capture());";
                    ctMethod.insertBefore(code);
                }


                // 更新 run方法
                CtMethod method = clazz.getDeclaredMethod("run");


                String code2 = "Object captured = capturedRef.get();";
                String code3 = "backup = com.alibaba.ttl.TransmittableThreadLocal.Transmitter.replay(captured);";
                method.insertBefore(code2 + code3);

                String code4 = "com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(backup);";
                method.insertAfter(code4);
                return clazz.toBytecode();
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void updateMethodOfExecutorClass(final CtMethod method) throws NotFoundException, CannotCompileException {
        final int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) return;

        CtClass[] parameterTypes = method.getParameterTypes();
        StringBuilder insertCode = new StringBuilder();
        for (int i = 0; i < parameterTypes.length; i++) {
            final String paramTypeName = parameterTypes[i].getName();
//            if (PARAM_TYPE_NAME_TO_DECORATE_METHOD_CLASS.containsKey(paramTypeName)) {
//                String code = String.format("$%d = %s.get($%d, false, true);", i + 1, PARAM_TYPE_NAME_TO_DECORATE_METHOD_CLASS.get(paramTypeName), i + 1);
//                logger.info("insert code before method " + signatureOfMethod(method) + " of class " + method.getDeclaringClass().getName() + ": " + code);
//                insertCode.append(code);
//            }
        }
        if (insertCode.length() > 0) method.insertBefore(insertCode.toString());
    }
}
