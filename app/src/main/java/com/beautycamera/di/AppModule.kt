package com.beautycamera.di

import android.content.Context
import com.beautycamera.BuildConfig
import com.beautycamera.data.remote.GeminiApiService
import com.beautycamera.data.remote.HuggingFaceApiService
import com.beautycamera.data.remote.PythonBackendApiService
import com.beautycamera.data.remote.StabilityAiApiService
import com.beautycamera.data.remote.TogetherAiApiService
import com.beautycamera.data.repository.FilterRepository
import com.beautycamera.data.repository.GeminiRepository
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.data.repository.PythonBackendRepository
import com.beautycamera.data.repository.SDRepository
import com.beautycamera.data.repository.SettingsDataStore
import com.beautycamera.data.repository.StickerRepository
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.CannyPreprocessor
import com.beautycamera.util.GPUImageHelper
import com.beautycamera.util.MediaPipeHelper
import com.beautycamera.util.OpenCVHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    // ── Stability AI ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("StabilityClient")
    fun provideStabilityOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.STABILITY_AI_API_KEY}")
                    .addHeader("Accept", "image/*")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("StabilityRetrofit")
    fun provideStabilityRetrofit(@Named("StabilityClient") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.stability.ai/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideStabilityAiApiService(@Named("StabilityRetrofit") retrofit: Retrofit): StabilityAiApiService =
        retrofit.create(StabilityAiApiService::class.java)

    // ── Together AI ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("TogetherClient")
    fun provideTogetherOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.TOGETHER_API_KEY}")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("TogetherRetrofit")
    fun provideTogetherRetrofit(@Named("TogetherClient") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.together.xyz/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideTogetherAiApiService(@Named("TogetherRetrofit") retrofit: Retrofit): TogetherAiApiService =
        retrofit.create(TogetherAiApiService::class.java)

    // ── HuggingFace ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("HFClient")
    fun provideHuggingFaceOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.HF_TOKEN}")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("HFRetrofit")
    fun provideHuggingFaceRetrofit(@Named("HFClient") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideHuggingFaceApiService(@Named("HFRetrofit") retrofit: Retrofit): HuggingFaceApiService =
        retrofit.create(HuggingFaceApiService::class.java)

    @Provides
    @Singleton
    fun provideSDRepository(
        stabilityApi: StabilityAiApiService,
        togetherApi: TogetherAiApiService,
        hfApi: HuggingFaceApiService,
        bitmapUtils: BitmapUtils,
        cannyPreprocessor: CannyPreprocessor,
    ): SDRepository = SDRepository(
        stabilityApi = stabilityApi,
        togetherApi = togetherApi,
        hfApi = hfApi,
        bitmapUtils = bitmapUtils,
        cannyPreprocessor = cannyPreprocessor,
    )

    // ── Python Local Backend ──────────────────────────────────────────────────

    /**
     * Base URL for the local Python AI backend.
     *   - Android Emulator : http://10.0.2.2:8000/   (loopback to host machine)
     *   - Real device (LAN): http://<HOST_LAN_IP>:8000/
     *
     * Change PYTHON_BACKEND_URL below to your LAN IP when testing on a real device.
     */
    @Provides
    @Singleton
    @Named("PythonClient")
    fun providePythonOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)    // fast fail if server not running
            .readTimeout(180, TimeUnit.SECONDS)       // generation can take up to 3 min
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("PythonRetrofit")
    fun providePythonRetrofit(@Named("PythonClient") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://192.168.1.15:8000/")     // real device → host LAN IP
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePythonBackendApiService(@Named("PythonRetrofit") retrofit: Retrofit): PythonBackendApiService =
        retrofit.create(PythonBackendApiService::class.java)

    @Provides
    @Singleton
    fun providePythonBackendRepository(
        apiService: PythonBackendApiService,
    ): PythonBackendRepository = PythonBackendRepository(apiService)

    // ── Gemini AI ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("GeminiClient")
    fun provideGeminiOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("GeminiRetrofit")
    fun provideGeminiRetrofit(@Named("GeminiClient") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideGeminiApiService(@Named("GeminiRetrofit") retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    @Provides
    @Singleton
    fun provideGeminiRepository(
        geminiApi: GeminiApiService,
        pythonBackendRepository: PythonBackendRepository,
    ): GeminiRepository = GeminiRepository(geminiApi, pythonBackendRepository)

    // ── Repositories ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePhotoRepository(): PhotoRepository = PhotoRepository()

    @Provides
    @Singleton
    fun provideFilterRepository(@ApplicationContext context: Context): FilterRepository =
        FilterRepository(context)

    @Provides
    @Singleton
    fun provideStickerRepository(@ApplicationContext context: Context): StickerRepository =
        StickerRepository(context)

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideBitmapUtils(): BitmapUtils = BitmapUtils()

    @Provides
    @Singleton
    fun provideCannyPreprocessor(): CannyPreprocessor = CannyPreprocessor()

    @Provides
    @Singleton
    fun provideGPUImageHelper(@ApplicationContext context: Context): GPUImageHelper =
        GPUImageHelper(context)

    @Provides
    @Singleton
    fun provideMediaPipeHelper(@ApplicationContext context: Context): MediaPipeHelper =
        MediaPipeHelper(context)

    @Provides
    @Singleton
    fun provideOpenCVHelper(): OpenCVHelper = OpenCVHelper()
}
