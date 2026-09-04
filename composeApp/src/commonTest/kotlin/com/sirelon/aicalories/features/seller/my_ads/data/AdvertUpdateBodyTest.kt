package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateOptionalKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateRequiredKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `PUT adverts/{id}` takes the whole advert, not a patch, so the body is rebuilt from the `GET`
 * response on every edit. Three releases went out with that body wrong - OLX answered each with
 * "compound forms expect an array or NULL on submission" - because nothing here checked its shape.
 *
 * [specResponseExample] is the response sample from the OLX spec, verbatim, so these assertions
 * are pinned to the published contract rather than to one advert on one market.
 */
class AdvertUpdateBodyTest {

    /**
     * The `Advert` example from the OLX API spec, field for field: `location` at the top level,
     * `salary` and `courier` null, and attributes carrying a scalar `value` with `values: null` -
     * the second of them numeric, which is how the spec itself writes it.
     *
     * The trailing four keys are not from the example. They are keys the live API sends that the
     * update schema does not define, which is precisely what must not be echoed back.
     */
    private val specResponseExample = """
        {
          "id": 123,
          "status": "active",
          "url": "https://www.olx.ua/oferta/url.html",
          "created_at": "2018-02-02 09:35:16",
          "activated_at": "2018-02-02 09:32:52",
          "valid_to": "2018-03-04 09:32:52",
          "title": "This is title",
          "description": "This is description",
          "category_id": 123,
          "advertiser_type": "private",
          "external_id": "12345",
          "external_url": "http://myshop.com/advert/123",
          "contact": {"name": "John", "phone": "2341235435,1245134254,12452145"},
          "location": {"city_id": 1, "district_id": null, "latitude": 53.123, "longitude": 17.123},
          "images": [
            {"url": "https://www.olx.ua/advert-picture-1.jpg"},
            {"url": "https://www.olx.ua/advert-picture-2.jpg"}
          ],
          "price": {"value": 123, "currency": "PLN", "negotiable": false, "trade": false, "budget": false},
          "salary": null,
          "attributes": [
            {"code": "model", "value": "cts", "values": null},
            {"code": "year", "value": 2015, "values": null}
          ],
          "courier": null,
          "auto_extend_enabled": true,
          "ad_delivery": {"enabled": true, "carrier": "nova_poshta", "delivery_change_allowed": false},
          "delivery_change_allowed": false,
          "moderation_reason": "none",
          "some_key_a_market_added_after_this_was_written": {"nested": ["value1", 2, true]}
        }
    """.trimIndent().let { Json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.updateBody(
        title: String = "This is title",
        description: String = "This is description",
        priceValue: Long? = null,
        fallbackCurrencyCode: String = "UAH",
    ) = toUpdateBody(title, description, priceValue, fallbackCurrencyCode)

    @Test
    fun `sends nothing the update schema does not define`() {
        val body = specResponseExample.updateBody()

        val documented = AdvertUpdateRequiredKeys + AdvertUpdateOptionalKeys
        assertEquals(
            emptySet(),
            body.keys - documented,
            "every key sent must be one PUT adverts/{id} defines",
        )

        // Named individually because each was in the payload that OLX refused.
        assertNull(body["ad_delivery"], "ad_delivery is not an update field")
        assertNull(body["delivery_change_allowed"], "delivery_change_allowed is not an update field")
        assertNull(body["status"], "status is set by OLX, never submitted")
        assertNull(body["some_key_a_market_added_after_this_was_written"])
    }

    @Test
    fun `sends every field the update schema requires`() {
        val body = specResponseExample.updateBody()

        assertEquals(
            emptySet(),
            AdvertUpdateRequiredKeys - body.keys,
            "a body missing a required field is rejected outright",
        )
    }

    @Test
    fun `an attribute keeps the key it arrived under, and a numeric value becomes a string`() {
        // `value` and `values` are different fields in the schema - one for an attribute that
        // takes a single value, one for an attribute that takes several. Coercing every attribute
        // into a `values` array was one of the three guesses that shipped.
        val body = specResponseExample.updateBody()

        assertEquals(
            Json.parseToJsonElement(
                """[{"code": "model", "value": "cts"}, {"code": "year", "value": "2015"}]""",
            ),
            body["attributes"],
        )
    }

    @Test
    fun `an attribute holding several values is sent as an array`() {
        val body = buildJsonObject {
            put("category_id", 12)
            putJsonArrayOfAttributes()
        }.updateBody()

        assertEquals(
            Json.parseToJsonElement("""[{"code": "extras", "values": ["abs", "gps"]}]"""),
            body["attributes"],
        )
    }

    @Test
    fun `an advert with no attributes still sends the required empty array`() {
        val body = buildJsonObject { put("category_id", 12) }.updateBody()

        assertEquals(JsonArray(emptyList()), body["attributes"])
    }

    @Test
    fun `an attribute with neither value is dropped rather than sent as the text null`() {
        // JsonNull is itself a JsonPrimitive, so reading `.content` off it yields "null".
        val body = Json.parseToJsonElement(
            """{"category_id": 12, "attributes": [{"code": "empty", "value": null, "values": null}]}""",
        ).jsonObject.updateBody()

        assertEquals(JsonArray(emptyList()), body["attributes"])
    }

    @Test
    fun `objects are narrowed to the properties the schema lists for them`() {
        val body = specResponseExample.updateBody()

        // `location` is required top-level and the response's null district is not sent back.
        assertEquals(
            Json.parseToJsonElement("""{"city_id": 1, "latitude": 53.123, "longitude": 17.123}"""),
            body["location"],
        )
        assertEquals(
            Json.parseToJsonElement("""{"name": "John", "phone": "2341235435,1245134254,12452145"}"""),
            body["contact"],
        )
        assertEquals(
            Json.parseToJsonElement("""[{"url": "https://www.olx.ua/advert-picture-1.jpg"}, {"url": "https://www.olx.ua/advert-picture-2.jpg"}]"""),
            body["images"],
        )
    }

    @Test
    fun `a location nested under contact is still found and sent where the schema wants it`() {
        // The spec puts location at the top level; this covers a response that does not.
        val body = Json.parseToJsonElement(
            """{"category_id": 12, "contact": {"name": "John", "location": {"city_id": 7}}}""",
        ).jsonObject.updateBody()

        assertEquals(Json.parseToJsonElement("""{"city_id": 7}"""), body["location"])
        assertEquals(Json.parseToJsonElement("""{"name": "John"}"""), body["contact"])
    }

    @Test
    fun `auto extend is never sent, so an edit leaves the seller's renew setting alone`() {
        // The one field the spec documents as unchanged when omitted.
        assertNull(specResponseExample.updateBody()["auto_extend_enabled"])
        assertTrue(specResponseExample["auto_extend_enabled"] != null, "the response does carry it")
    }

    @Test
    fun `editing the price replaces the value and keeps the currency OLX already had`() {
        val price = specResponseExample.updateBody(priceValue = 999)["price"]

        assertEquals(
            Json.parseToJsonElement(
                """{"value": 999, "currency": "PLN", "negotiable": false, "trade": false, "budget": false}""",
            ),
            price,
        )
    }

    @Test
    fun `a price on an advert that had none is given the account's currency`() {
        // OLX will not take a value without a currency alongside it.
        val body = buildJsonObject { put("category_id", 12) }.updateBody(
            priceValue = 500,
            fallbackCurrencyCode = "UAH",
        )

        assertEquals(Json.parseToJsonElement("""{"value": 500, "currency": "UAH"}"""), body["price"])
    }

    @Test
    fun `leaving the price alone keeps what OLX returned untouched`() {
        val body = specResponseExample.updateBody(priceValue = null)

        assertEquals(specResponseExample["price"], body["price"])
    }

    @Test
    fun `the edited title and description are the ones sent`() {
        val body = specResponseExample.updateBody(title = "New title", description = "New body")

        assertEquals("New title", body["title"]?.toString()?.trim('"'))
        assertEquals("New body", body["description"]?.toString()?.trim('"'))
    }

    @Test
    fun `an advert with no advertiser type is submitted as private`() {
        // Required by the schema, so it cannot be omitted, and a seller using this app is private.
        val body = buildJsonObject { put("category_id", 12) }.updateBody()

        assertEquals("private", body["advertiser_type"]?.toString()?.trim('"'))
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayOfAttributes() {
    put("attributes", Json.parseToJsonElement("""[{"code": "extras", "values": ["abs", "gps"]}]"""))
}
