package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class SberLoginRequest(
    @SerializedName("code_challenge")
    val codeChallenge: String,
    @SerializedName("code_challenge_method")
    val codeChallengeMethod: String = "S256",
    @SerializedName("redirect_uri")
    val redirectUri: String,
)

data class SberLoginUrlResponse(
    @SerializedName("authorize_url")
    val authorizeUrl: String,
)

data class SberCallbackRequest(
    @SerializedName("code")
    val code: String,
    @SerializedName("code_verifier")
    val codeVerifier: String,
    @SerializedName("redirect_uri")
    val redirectUri: String,
    @SerializedName("state")
    val state: String,
)
