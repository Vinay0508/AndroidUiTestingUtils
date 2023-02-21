package sergio.sastre.uitesting.utils.testrules.locale

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate.getApplicationLocales
import androidx.appcompat.app.AppCompatDelegate.setApplicationLocales
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import sergio.sastre.uitesting.utils.common.LocaleUtil
import java.util.*

/**
 * A TestRule to change the in-app locales of the application ONLY.
 * The Locale of the System does not change. Use SystemLocaleTestRule instead for that.
 * Beware that in-app locales prevail over the system locale while displaying texts.
 *
 * WARNING: This TestRule works on API 32 or lower if autoStoreLocale = false.
 * Otherwise, tt is not ensured to work as expected
 * That's why it's strongly recommended to disable it in your debug-manifest.
 * For instance, by including the following in your debug-manifest:
 *
 * <service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
 *    android:enabled="false"
 *    android:exported="false"
 *    tools:node="replace"
 * >
 *    <meta-data
 *       android:name="autoStoreLocales"
 *       android:value="false"
 *    />
 *</service>
 **/
class InAppLocaleTestRule constructor(private val locale: Locale) : TestRule {

    companion object {
        private val TAG = InAppLocaleTestRule::class.java.simpleName
    }

    private lateinit var initialLocales: LocaleListCompat

    constructor(testLocale: String) : this(LocaleUtil.localeFromString(testLocale))

    private val appLocalesLanguageTags
        get() = getApplicationLocales().toLanguageTags().ifBlank { "empty" }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                try {
                    // From API 33 we need to ensure that AppCompatDelegate.setApplicationLocales
                    // is called after onActivityCreated
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setApplicationLocaleAfterActivityOnCreate(locale)
                    } else {
                        setApplicationLocaleInLooper(Looper.getMainLooper(), locale)
                    }
                    base.evaluate()
                } catch (throwable: Throwable) {
                    val testName = "${description.testClass.simpleName}\$${description.methodName}"
                    val errorMessage =
                        "Test $testName failed on setting inAppLocale to ${locale.toLanguageTag()}"
                    Log.e(TAG, errorMessage)
                    throw throwable
                } finally {
                    // must run on Main thread to avoid IllegalStateExceptions
                    Handler(Looper.getMainLooper()).post {
                        setApplicationLocales(initialLocales)
                        Log.d(TAG, "in-app locales reset to $appLocalesLanguageTags")
                    }
                }
            }
        }
    }

    private fun setApplicationLocaleInLooper(looper: Looper, locale: Locale?) {
        Handler(looper).post {
            initialLocales = getApplicationLocales()
            Log.d(TAG, "initial in-app locales is $appLocalesLanguageTags")
            setApplicationLocales(LocaleListCompat.create(locale))
            Log.d(TAG, "in-app locales set to $appLocalesLanguageTags")
        }
    }


    private fun setApplicationLocaleAfterActivityOnCreate(locale: Locale?) {
        ApplicationProvider.getApplicationContext<Application>().apply {
            registerActivityLifecycleCallbacks(
                object : OnActivityCreatedCallback {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?
                    ) {
                        unregisterActivityLifecycleCallbacks(this)
                        setApplicationLocaleInLooper(activity.mainLooper, locale)
                    }
                }
            )
        }
    }
}
