/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.data.mangasync.anilist

import com.google.gson.Gson
import eu.kanade.tachiyomi.data.mangasync.anilist.model.OAuth
import okhttp3.Interceptor
import okhttp3.Response

class AnilistInterceptor(private var refreshToken: String?) : Interceptor {

    /**
     * OAuth object used for authenticated requests.
     *
     * Anilist returns the date without milliseconds. We fix that and make the token expire 1 minute
     * before its original expiration date.
     */
    private var oauth: OAuth? = null
        set(value) {
            field = value?.copy(expires = value.expires * 1000 - 60 * 1000)
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (refreshToken.isNullOrEmpty()) {
            throw Exception("Not authenticated with Anilist")
        }

        // Refresh access token if null or expired.
        if (oauth == null || oauth!!.isExpired()) {
            val response = chain.proceed(AnilistApi.refreshTokenRequest(refreshToken!!))
            oauth = if (response.isSuccessful) {
                Gson().fromJson(response.body().string(), OAuth::class.java)
            } else {
                response.close()
                null
            }
        }

        // Throw on null auth.
        if (oauth == null) {
            throw Exception("Access token wasn't refreshed")
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.access_token}")
                .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with Anilist for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: OAuth?) {
        refreshToken = oauth?.refresh_token
        this.oauth = oauth
    }

}