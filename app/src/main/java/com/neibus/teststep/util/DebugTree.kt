package com.neibus.teststep.util

import timber.log.Timber

class DebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String? {
        return "NEIBUS# (${element.fileName}:${element.lineNumber}) [MethodName :${element.methodName}]"
    }
}