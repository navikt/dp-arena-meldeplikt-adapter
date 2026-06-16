package no.nav.dagpenger.arenameldepliktadapter.utils

import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

val tokenXClient: CachedOauth2Client by lazy {
    val config = HashMap<String, String>()
    config["TOKEN_X_CLIENT_ID"] =
        getEnv("TOKEN_X_CLIENT_ID") ?: throw IllegalStateException("TOKEN_X_CLIENT_ID er ikke satt")
    config["TOKEN_X_PRIVATE_JWK"] =
        getEnv("TOKEN_X_PRIVATE_JWK") ?: throw IllegalStateException("TOKEN_X_PRIVATE_JWK er ikke satt")
    config["TOKEN_X_WELL_KNOWN_URL"] =
        getEnv("TOKEN_X_WELL_KNOWN_URL") ?: throw IllegalStateException("TOKEN_X_WELL_KNOWN_URL er ikke satt")

    val tokenXConfig = OAuth2Config.TokenX(config)
    CachedOauth2Client(
        tokenEndpointUrl = tokenXConfig.tokenEndpointUrl,
        authType = tokenXConfig.privateKey(),
    )
}

val azureAdClient: CachedOauth2Client by lazy {
    val config = HashMap<String, String>()
    config["AZURE_APP_CLIENT_ID"] =
        getEnv("AZURE_APP_CLIENT_ID") ?: throw IllegalStateException("AZURE_APP_CLIENT_ID er ikke satt")
    config["AZURE_APP_CLIENT_SECRET"] =
        getEnv("AZURE_APP_CLIENT_SECRET") ?: throw IllegalStateException("AZURE_APP_CLIENT_SECRET er ikke satt")
    config["AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"] = getEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
        ?: throw IllegalStateException("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT er ikke satt")
    config["AZURE_APP_WELL_KNOWN_URL"] =
        getEnv("AZURE_APP_WELL_KNOWN_URL") ?: throw IllegalStateException("AZURE_APP_WELL_KNOWN_URL er ikke satt")

    val azureAdConfig = OAuth2Config.AzureAd(config)
    CachedOauth2Client(
        tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
        authType = azureAdConfig.clientSecret(),
    )
}

fun tokenXExchanger(token: String, audience: String): () -> String = {
    if (isCurrentlyRunningLocally()) {
        ""
    } else {
        runBlocking { tokenXClient.tokenExchange(token, audience).access_token ?: "" }
    }
}

fun azureAdExchanger(scope: String): () -> String = {
    if (isCurrentlyRunningLocally()) {
        ""
    } else {
        runBlocking { azureAdClient.clientCredentials(scope).access_token ?: "" }
    }
}

fun hentIdentFraToken(authString: String?): String? {
    val decodedToken = decodeToken(authString)

    return extractSubject(decodedToken)
}

fun hentTokenX(authString: String?): Pair<String, String> {
    val incomingToken = authString?.replace("Bearer ", "") ?: ""
    val ident = hentIdentFraToken(authString) ?: ""

    val tokenProvider = tokenXExchanger(incomingToken, getEnv("MELDEKORTSERVICE_AUDIENCE") ?: "")
    val token = tokenProvider.invoke()

    return Pair(ident, token)
}

fun hentAzureToken(scope: String): String {
    val tokenProvider = azureAdExchanger(scope)
    return tokenProvider.invoke()
}

fun isAzureToken(authString: String?): Boolean {
    val decodedToken = decodeToken(authString)

    return decodedToken?.issuer?.contains("microsoftonline.com") == true
}
