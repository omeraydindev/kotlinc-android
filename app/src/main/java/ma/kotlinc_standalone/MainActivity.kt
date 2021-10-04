package ma.kotlinc_standalone

import android.app.Activity
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var etCode: EditText
    private lateinit var btnRun: Button
    private lateinit var logs: TextView

    private val rtJar by lazy {
        val file = File(cacheDir, "rt.jar")

        file.outputStream().use { output ->
            assets.open("rt.jar").use { input ->
                input.copyTo(output)
            }
        }

        file
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCode = findViewById(R.id.et_code)
        btnRun = findViewById(R.id.btn_run)
        logs = findViewById(R.id.logs)

        btnRun.setOnClickListener {
            logs.text = ""
            val dialog = ProgressDialog.show(this,
                "Loading", "Invoking kotlinc...", true, false)

            val handler = Handler(Looper.getMainLooper())

            Executors.newSingleThreadExecutor().execute {
                compileKtTest()

                handler.post(dialog::dismiss)
            }
        }
    }

    private fun compileKtTest() {
        val mKotlinHome  = File(cacheDir, "kt_home").apply { mkdirs() }
        val mClassOutput = File(cacheDir, "classes").apply { mkdirs() }

        val fileToCompile = File(cacheDir, "Test.kt").apply {
            writeText(etCode.text.toString())
        }

        val compiler = K2JVMCompiler()
        val collector = object : MessageCollector {
            private val diagnostics = mutableListOf<Diagnostic>()

            override fun clear() { diagnostics.clear() }

            override fun hasErrors() = diagnostics.any { it.severity.isError }

            override fun report(
                severity: CompilerMessageSeverity,
                message: String,
                location: CompilerMessageSourceLocation?
            ) {
                diagnostics += Diagnostic(severity, message, location)
            }

            override fun toString() = diagnostics
                .joinToString(System.lineSeparator().repeat(2)) { it.toString() }
        }

        val arguments = mutableListOf<String>().apply {
            // Classpath
            add("-cp")
            add(rtJar.absolutePath)

            // Sources (.java & .kt)
            add(fileToCompile.absolutePath)
        }

        val args = K2JVMCompilerArguments().apply {
            compileJava = false
            includeRuntime = false
            noJdk = true
            noReflect = true
            noStdlib = true
            kotlinHome = mKotlinHome.absolutePath
            destination = mClassOutput.absolutePath
        }

        Log.d("TAG", "Running kotlinc with these arguments: $arguments")

        compiler.parseArguments(arguments.toTypedArray(), args)
        compiler.exec(collector, Services.EMPTY, args)

        runOnUiThread {
            logs.text = collector.toString()
            Toast.makeText(this, "Done, check ${mClassOutput.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class Diagnostic(
        val severity: CompilerMessageSeverity,
        val message: String,
        val location: CompilerMessageSourceLocation?
    )

}
