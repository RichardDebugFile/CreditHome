package com.alfas.amortizacioncredito.config

import com.alfas.amortizacioncredito.BuildConfig

/**
 * Objeto central de configuración de la aplicación.
 * Mapea las constantes y URLs inyectadas por Gradle según la variante de compilación (debug, qa, release).
 */
object AppConfig {

    val BASE_URL: String = BuildConfig.BASE_URL
    val VPS_SERVER_URL: String = BuildConfig.VPS_SERVER_URL
    val IS_PRODUCTION: Boolean = BuildConfig.IS_PRODUCTION
    val APP_CRYPTO_SALT: String = BuildConfig.APP_CRYPTO_SALT

    object RabbitMq {
        val DEFAULT_HOST: String = BuildConfig.RABBITMQ_HOST
        val DEFAULT_PORT: Int = BuildConfig.RABBITMQ_PORT
        val DEFAULT_USER: String = BuildConfig.RABBITMQ_USER
        val DEFAULT_PASSWORD: String = BuildConfig.RABBITMQ_PASSWORD
        val DEFAULT_VHOST: String = BuildConfig.RABBITMQ_VHOST
        val DEFAULT_EMAIL_QUEUE: String = BuildConfig.RABBITMQ_EMAIL_QUEUE
        val DEFAULT_SYNC_QUEUE: String = BuildConfig.RABBITMQ_SYNC_QUEUE
    }

    object Smtp {
        val DEFAULT_HOST: String = BuildConfig.SMTP_HOST
        val DEFAULT_PORT: Int = BuildConfig.SMTP_PORT
        val DEFAULT_SENDER_NAME: String = BuildConfig.SMTP_SENDER_NAME
    }
}
