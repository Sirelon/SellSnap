package com.sirelon.sellsnap.features.seller.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scam rules (SIR-176). Every text is an invented example in the shape of the real phishing
 * messages: an OLX-delivery notice with a confirm request or a deadline, and a link that leaves
 * OLX. The clean cases matter as much: a false alarm on a real buyer costs more than a miss.
 */
class ScamSignalsTest {

    private fun signals(text: String) = ScamSignals.detect(text)

    private fun assertSignals(text: String, vararg expected: String) =
        assertEquals(expected.toList().sorted(), signals(text), text)

    private fun assertClean(text: String) = assertEquals(emptyList(), signals(text), text)

    // The real thread that started this ticket: an OLX notice with a Telegram bot link.
    @Test
    fun `the OLX delivery notice with a bot link flags both signals`() {
        assertSignals(
            "Ваш товар придбано з OLX Доставкою. Підтвердіть отримання оплати протягом 3 годин: https://t.me/olx_pay_bot",
            ScamSignals.MESSENGER_LINK,
            ScamSignals.FAKE_OLX_DELIVERY,
        )
    }

    // messenger_link

    @Test
    fun `messenger links and bot handles flag messenger_link`() {
        listOf(
            "Напишіть мені: t.me/some_buyer",
            "https://telegram.me/some_buyer",
            "Write on whatsapp https://wa.me/380501234567",
            "https://api.whatsapp.com/send?phone=380501234567",
            "Zadzwoń viber://chat?number=%2B380501234567",
            "Hello, contact @OlxSupport_bot to continue",
            "Pisz tu: WWW.T.ME/kupiec",
        ).forEach { assertSignals(it, ScamSignals.MESSENGER_LINK) }
    }

    @Test
    fun `a plain mention of a messenger or an e-mail does not flag`() {
        assertClean("Можу написати вам у Telegram чи Viber?")
        assertClean("Pot să vă scriu pe WhatsApp?")
        assertClean("Write me at john@abbot.com")
        assertClean("Is the robot vacuum still available?")
        assertClean("Contact the shop on format.me soon")
    }

    // short_link

    @Test
    fun `URL shorteners flag short_link`() {
        listOf(
            "Перейдіть https://bit.ly/3AbCdE",
            "zobacz tinyurl.com/y8k2x9",
            "Faceți clic: cutt.ly/aB12",
            "Clique aqui https://t.ly/Xy9z",
            "Потвърдете тук is.gd/qwE12r",
            "see goo.su/AbC12 now",
            "Follow clck.ru/34abc",
        ).forEach { assertSignals(it, ScamSignals.SHORT_LINK) }
    }

    @Test
    fun `a shortener name with no path or inside another word is not a link`() {
        assertClean("I saw it on bit.ly yesterday")
        assertClean("Check habit.ly/abc")
    }

    // fake_olx_delivery, per language

    @Test
    fun `fake OLX delivery wording flags in every market language`() {
        listOf(
            // uk
            "Замовлення через OLX Доставка оплачено. Підтвердіть угоду: https://order.example.com/p/123",
            // pl
            "Kupiono przez OLX Dostawa, potwierdź w ciągu 3 godzin https://odbierz.example.com/p",
            // ro
            "Articolul a fost cumpărat cu livrare OLX. Confirmați în 3 ore: https://confirm.example.com/p",
            // pt
            "Compra com entrega OLX. Confirme o pagamento https://pagamento.example.com/p",
            // bg
            "Поръчка с доставка от OLX. Потвърдете в рамките на 3 часа https://pay.example.com/p",
            // kk
            "OLX жеткізу арқылы төлем жасалды. Растаңыз: https://pay.example.com/p",
            // en
            "Your item was bought with OLX delivery. Confirm within 3 hours: https://pay.example.com/p",
            "OLX Delivery: receive your payment here https://pay.example.com/p",
        ).forEach { text ->
            assertEquals(true, ScamSignals.FAKE_OLX_DELIVERY in signals(text), text)
        }
    }

    @Test
    fun `OLX delivery matches case-insensitively`() {
        assertEquals(
            true,
            ScamSignals.FAKE_OLX_DELIVERY in signals("YOUR PARCEL VIA OLX DELIVERY: CONFIRM HERE HTTPS://PAY.EXAMPLE.COM/X"),
        )
    }

    @Test
    fun `a real buyer who paid with OLX delivery and sent no link stays clean`() {
        assertClean("Я оформив OLX Доставку, підтвердіть замовлення в застосунку, будь ласка")
        assertClean("Kupiłem z OLX Dostawą, potwierdź zamówienie w aplikacji")
        assertClean("Am comandat cu livrare OLX, vă rog confirmați în aplicație în 3 ore")
        assertClean("Paid with OLX delivery, please confirm within 3 hours in the app")
    }

    @Test
    fun `an OLX link alone with delivery wording stays clean`() {
        assertClean("Оформив OLX Доставку, підтвердіть: https://www.olx.ua/myaccount/answers/")
    }

    @Test
    fun `a link and a confirm word without OLX delivery is not fake_olx_delivery`() {
        assertEquals(
            false,
            ScamSignals.FAKE_OLX_DELIVERY in signals("Please confirm you are still selling, photos here https://example.com/p/1"),
        )
    }

    // card_request

    @Test
    fun `asking for CVV, expiry or an SMS code flags card_request in every market language`() {
        listOf(
            "Введіть CVV та термін дії картки",
            "Podaj CVV i datę ważności karty",
            "Trimiteți codul din SMS primit de la bancă",
            "Preciso do código de verificação que recebeu",
            "Изпратете ми SMS код от банката",
            "Растау коды келді, айтыңызшы",
            "Send me the SMS code and the CVC",
            "Podaj kod BLIK, żebym zapłacił",
            "Enter the card expiry date",
            "Надішліть код з SMS",
        ).forEach { assertSignals(it, ScamSignals.CARD_REQUEST) }
    }

    @Test
    fun `a card number alone is a transfer request, with a link it flags`() {
        assertClean("Скиньте номер картки, я переведу гроші")
        assertClean("Podaj numer karty, zrobię przelew")
        assertClean("Send me your card number and I will transfer it")
        assertSignals(
            "Введіть номер картки тут https://secure-pay.example.com/card",
            ScamSignals.CARD_REQUEST,
        )
    }

    // olx_lookalike_link

    @Test
    fun `a host that imitates OLX flags olx_lookalike_link`() {
        listOf(
            "https://olx-delivery.example.com/order/1",
            "olx.secure-pay.example/confirm",
            "https://olx.ua.confirm-order.example/x",
            "https://olx.ua@evil.example/x",
            "olx-dostawa.example",
        ).forEach { assertEquals(true, ScamSignals.OLX_LOOKALIKE_LINK in signals(it), it) }
    }

    @Test
    fun `official OLX links stay clean`() {
        listOf(
            "https://www.olx.ua/d/uk/obyavlenie/iphone-13-IDabc12.html",
            "https://m.olx.pl/d/oferta/rower-CID767-IDabc.html",
            "https://www.olx.ro/d/oferta/telefon-IDabc.html",
            "https://www.olx.pt/d/anuncio/mesa-IDabc.html",
            "https://www.olx.bg/d/ad/kolo-IDabc.htm",
            "https://www.olx.kz/d/obyavlenie/abc.html",
            "olx.ua/d/uk/obyavlenie/x",
            "Ось: OLX.UA/d/uk/obyavlenie/x",
        ).forEach { assertClean(it) }
    }

    // Clean buyer messages.

    @Test
    fun `ordinary buyer messages stay clean`() {
        listOf(
            "Is it still available?",
            "Can you do 1400?",
            "Добрий день, ще актуально?",
            "Czy cena jest do negocjacji?",
            "Este disponível? Posso ir buscar hoje.",
            "Здравейте, още ли е налично?",
            "Бұл әлі сатылымда ма?",
            "Sunt interesat, mai e disponibil?",
            "Мій номер +380 67 123 45 67, зателефонуйте",
            "Call me on +48 600 123 456",
            "Ось схоже оголошення https://www.olx.ua/d/uk/obyavlenie/iphone-12-IDxyz.html — ціна нижча",
            "Дякую, заберу завтра о 15:00",
            "",
            "   ",
        ).forEach { assertClean(it) }
    }

    @Test
    fun `signals come back sorted and without repeats`() {
        val found = signals("https://t.me/x_bot https://bit.ly/abc введіть CVV")
        assertEquals(found.sorted(), found)
        assertEquals(found.distinct(), found)
        assertEquals(
            listOf(ScamSignals.CARD_REQUEST, ScamSignals.MESSENGER_LINK, ScamSignals.SHORT_LINK),
            found,
        )
    }
}
