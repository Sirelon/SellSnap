package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributesResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoriesRootResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategorySuggestionResponse
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parses REAL OLX partner-API payloads (captured from https://www.olx.ua, 2026-07-20) through the
 * exact [Json] config the app uses, then through [CategoriesMapper], to guard against silent
 * schema drift in the categories / suggestion / attributes endpoints.
 *
 * Regenerate fixtures with the `olx-api-verify` skill if the OLX schema changes.
 */
class CategoriesResponseParsingTest {

    // Mirrors commonOlxHttpClientConfig() in OlxHttpClientFactory.kt
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val mapper = CategoriesMapper()

    @Test
    fun `categories payload parses and maps - parent_id 0 becomes root`() {
        // Real shape: id(int), name(str), parent_id(int, 0=root), is_leaf(bool), photos_limit(int)
        val body = """
            {"data":[
              {"id":36,"name":"Дитячий світ","parent_id":0,"photos_limit":8,"is_leaf":false},
              {"id":1,"name":"Нерухомість","parent_id":0,"photos_limit":24,"is_leaf":false},
              {"id":1757,"name":"Квартири","parent_id":1,"photos_limit":24,"is_leaf":false},
              {"id":1522,"name":"Нерухомість за кордоном","parent_id":1,"photos_limit":24,"is_leaf":true}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromString<OlxCategoriesRootResponse>(body)
        val mapped = parsed.data.orEmpty().mapNotNull(mapper::mapCategory)

        assertEquals(4, mapped.size)
        val root = mapped.first { it.id == 36 }
        assertEquals("Дитячий світ", root.label)
        assertNull(root.parentId, "parent_id 0 must map to null (root)")
        assertEquals(false, root.isLeaf)

        val child = mapped.first { it.id == 1522 }
        assertEquals(1, child.parentId)
        assertEquals(true, child.isLeaf)
    }

    @Test
    fun `mapCategory drops items without an id`() {
        val body = """{"data":[{"name":"No id","parent_id":0,"is_leaf":true},{"id":7,"name":"Ok","parent_id":0,"is_leaf":true}]}"""
        val mapped = json.decodeFromString<OlxCategoriesRootResponse>(body).data.orEmpty().mapNotNull(mapper::mapCategory)
        assertEquals(listOf(7), mapped.map { it.id })
    }

    @Test
    fun `category suggestion parses live string id and path`() {
        // Live suggestion returns id as a QUOTED STRING ("85") plus name/path we don't model.
        val body = """
            {"data":[{"id":"85","name":"Смартфони / мобільні телефони","path":[
              {"id":"37","name":"Електроніка"},{"id":"44","name":"Телефони та аксесуари"}]}]}
        """.trimIndent()

        val item = json.decodeFromString<OlxCategorySuggestionResponse>(body).data?.firstOrNull()
        assertEquals("85", item?.id, "OLX returns the suggested category id as a string")
        assertEquals(85, item?.id?.toIntOrNull(), "which loadCategorySuggestionId converts to the numeric id")
    }

    @Test
    fun `attributes payload parses and derives every input type`() {
        // Real attributes: select (state), multi-select (condition), numeric with int min/max
        // (motor_year), numeric with null min/max (power). values[].code is a numeric STRING.
        val body = """
            {"data":[
              {"code":"state","label":"Стан","unit":"","validation":{"type":"attribute","required":true,"numeric":false,"min":null,"max":null,"allow_multiple_values":false},"values":[{"code":"used","label":"Вживане"},{"code":"new","label":"Нове"}]},
              {"code":"condition","label":"Технічний стан","unit":"","validation":{"type":"attribute","required":true,"numeric":false,"min":null,"max":null,"allow_multiple_values":true},"values":[{"code":"not-bit","label":"Не бита"},{"code":"not-colored","label":"Не фарбована"}]},
              {"code":"motor_year","label":"Рік випуску","unit":"","validation":{"type":"attribute","required":true,"numeric":true,"min":1930,"max":2050,"allow_multiple_values":false},"values":[]},
              {"code":"power","label":"Потужність","unit":"к.с.","validation":{"type":"attribute","required":false,"numeric":true,"min":null,"max":null,"allow_multiple_values":false},"values":[]}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromString<OlxAttributesResponse>(body)
        val attrs = mapper.mapAttributes(parsed.data.orEmpty()).associateBy { it.code }
        assertEquals(4, attrs.size)

        assertEquals(AttributeInputType.SingleSelect, attrs.getValue("state").inputType)
        assertTrue(attrs.getValue("state").validationRules.required)
        assertEquals(listOf("used", "new"), attrs.getValue("state").allowedValues.map { it.code })

        assertEquals(AttributeInputType.MultiSelect, attrs.getValue("condition").inputType)

        val year = attrs.getValue("motor_year")
        assertEquals(AttributeInputType.NumericInput, year.inputType)
        assertEquals(1930.0, year.validationRules.min)
        assertEquals(2050.0, year.validationRules.max)

        val power = attrs.getValue("power")
        assertEquals(AttributeInputType.NumericInput, power.inputType)
        assertEquals("к.с.", power.unit)
        assertNull(power.validationRules.min)
    }

    @Test
    fun `attribute with no values and not numeric falls back to text input`() {
        val body = """{"data":[{"code":"free_text","label":"Опис","unit":"","validation":{"type":"attribute","required":false,"numeric":false,"min":null,"max":null,"allow_multiple_values":false},"values":[]}]}"""
        val attr = mapper.mapAttributes(json.decodeFromString<OlxAttributesResponse>(body).data.orEmpty()).single()
        assertEquals(AttributeInputType.TextInput, attr.inputType)
    }
}
