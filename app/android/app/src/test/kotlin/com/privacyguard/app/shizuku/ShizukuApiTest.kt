package com.privacyguard.app.shizuku

import org.junit.Assert.assertNotNull
import org.junit.Test
import rikka.shizuku.Shizuku
import java.lang.Process

class ShizukuApiTest {
    @Test
    fun testShizukuMethodsExist() {
        val newProcessMethod = Shizuku::class.java.declaredMethods.find { it.name == "newProcess" }
        println("newProcess method found: $newProcessMethod")
        if (newProcessMethod != null) {
            println("ReturnType: ${newProcessMethod.returnType.name}")
            println("Extends Process: ${Process::class.java.isAssignableFrom(newProcessMethod.returnType)}")
            println("ReturnType methods: ${newProcessMethod.returnType.methods.map { it.name }}")
        }
        assertNotNull(newProcessMethod)
    }
}
