package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.Product
import com.fitnessclub.app.data.api.PurchaseProductRequest
import com.fitnessclub.app.data.api.PurchaseProductResponse
import com.fitnessclub.app.ui.screens.shop.ShopCategory
import com.fitnessclub.app.ui.screens.shop.ShopItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val api: FitnessApi
) {
    suspend fun getProducts(): ApiResult<List<ShopItem>> {
        return try {
            val response = api.getProducts()
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.map { it.toShopItem() }
                ApiResult.Success(items)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки товаров", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun purchaseProduct(productId: String, quantity: Int = 1): ApiResult<PurchaseProductResponse> {
        return try {
            val response = api.purchaseProduct(productId, PurchaseProductRequest(quantity = quantity))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                ApiResult.Error(errorBody, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Ошибка покупки")
        }
    }
}

private fun Product.toShopItem(): ShopItem {
    val category = when (category.lowercase()) {
        "goods" -> ShopCategory.GOODS
        "service", "services" -> ShopCategory.SERVICES
        else -> ShopCategory.SERVICES
    }
    return ShopItem(
        id = id,
        name = name,
        description = description ?: "",
        price = price,
        oldPrice = null,
        category = category
    )
}
