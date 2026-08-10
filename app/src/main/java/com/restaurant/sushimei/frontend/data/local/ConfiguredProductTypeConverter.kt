package com.restaurant.sushimei.frontend.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct

/**
 * Convierte [List<ConfiguredProduct>] ↔ [String] JSON para Room.
 *
 * Room no sabe cómo serializar listas de objetos complejos.
 * Usamos Gson (ya en el proyecto como parte de Retrofit) para hacer la conversión.
 *
 * No usamos @TypeConverter de Room aquí directamente — en su lugar los usamos
 * dentro de [AppDatabase] con @TypeConverters. Esta clase solo encapsula la lógica.
 */
object ConfiguredProductTypeConverter {

    private val gson = Gson()
    private val listType = object : TypeToken<List<ConfiguredProduct>>() {}.type

    fun fromList(items: List<ConfiguredProduct>): String =
        gson.toJson(items)

    fun toList(json: String): List<ConfiguredProduct> =
        gson.fromJson(json, listType) ?: emptyList()
}
