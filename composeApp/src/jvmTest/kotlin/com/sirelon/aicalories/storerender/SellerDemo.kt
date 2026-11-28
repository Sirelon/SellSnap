package com.sirelon.sellsnap.storerender

/**
 * Demo content for the seller scenes (My Ads, listing statistics, messages), per language. Seller
 * content, not app copy: app copy comes from strings.xml through the real screens. Every person,
 * shop and listing is fictional (App Review 2.3.9). Prices are amounts in the market currency.
 */
class SellerDemo(
    val account: String,
    val shop: String,
    val bike: String,
    val guitar: String,
    val chair: String,
    val camera: String,
    val lamp: String,
    val jacket: String,
    val coffee: String,
    val guitarChat: GuitarChat,
    val scamChat: ScamChat,
)

/** What a buyer and the seller write about the guitar, and the AI's suggested answers. */
class GuitarChat(
    val buyerAsks: String,
    val sellerYes: String,
    val sellerCase: String,
    /** Buyer's last message; takes the offer, already formatted with its currency. */
    val buyerOffer: (String) -> String,
    /** Suggestion that keeps the asking price. */
    val suggestKeep: (String) -> String,
    /** Suggestion that accepts the counter and proposes a pickup; the model flags it for review. */
    val suggestAccept: (String) -> String,
)

/** A typical OLX Delivery phishing exchange about the bike. */
class ScamChat(
    val buyerAsks: String,
    val sellerYes: String,
    val buyerPaid: String,
    val buyerLink: String,
)

/** Asking prices per market currency. Guitar offer and counter are what the buyer and seller say. */
class SellerPrices(
    val bike: Long,
    val guitar: Long,
    val guitarOffer: Long,
    val guitarCounter: Long,
    val chair: Long,
    val camera: Long,
    val lamp: Long,
)

fun sellerPricesFor(currencyCode: String): SellerPrices = when (currencyCode) {
    "UAH" -> SellerPrices(bike = 7_500, guitar = 4_200, guitarOffer = 3_500, guitarCounter = 3_900, chair = 3_000, camera = 2_400, lamp = 1_900)
    "PLN" -> SellerPrices(bike = 700, guitar = 450, guitarOffer = 380, guitarCounter = 420, chair = 320, camera = 250, lamp = 200)
    "RON" -> SellerPrices(bike = 750, guitar = 480, guitarOffer = 400, guitarCounter = 450, chair = 350, camera = 260, lamp = 220)
    "KZT" -> SellerPrices(bike = 85_000, guitar = 48_000, guitarOffer = 40_000, guitarCounter = 44_000, chair = 35_000, camera = 28_000, lamp = 22_000)
    else -> SellerPrices(bike = 170, guitar = 95, guitarOffer = 80, guitarCounter = 88, chair = 70, camera = 55, lamp = 45)
}

private const val ScamLink = "https://olx-delivery.net/pay/48213"

fun sellerDemoFor(lang: StoreLang): SellerDemo = when (lang) {
    StoreLang.EN -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "City bike, 28\"",
        guitar = "Fender acoustic guitar",
        chair = "Vintage office chair",
        camera = "Pentax Optio camera",
        lamp = "Table lamp with shade",
        jacket = "Red puffer jacket, M",
        coffee = "Gastroback espresso machine",
        guitarChat = GuitarChat(
            buyerAsks = "Hello! Is the guitar still available?",
            sellerYes = "Hi! Yes, it is. Barely played, no scratches.",
            sellerCase = "The case is included, and I can add a new set of strings.",
            buyerOffer = { "Sounds good. Would you take $it if I pick it up today?" },
            suggestKeep = { "I'd rather keep $it, since the case and new strings are included." },
            suggestAccept = { "$it works for me. When can you come to pick it up?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Hello! Is the bike still for sale?",
            sellerYes = "Hi! Yes, it is.",
            buyerPaid = "Good. I live in another city, so I already paid with OLX Delivery.",
            buyerLink = "It can take up to 2 hours. Confirm the payment here to receive your money: $ScamLink",
        ),
    )

    StoreLang.UK -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Міський велосипед, 28\"",
        guitar = "Акустична гітара Fender",
        chair = "Вінтажне офісне крісло",
        camera = "Фотоапарат Pentax Optio",
        lamp = "Настільна лампа з абажуром",
        jacket = "Червоний пуховик, M",
        coffee = "Кавоварка Gastroback",
        guitarChat = GuitarChat(
            buyerAsks = "Добрий день! Гітара ще продається?",
            sellerYes = "Вітаю! Так, продається. Майже не грали, подряпин немає.",
            sellerCase = "Чохол у комплекті, новий набір струн додам.",
            buyerOffer = { "Добре. Віддасте за $it, якщо заберу сьогодні?" },
            suggestKeep = { "Волію залишити $it — у комплекті чохол і нові струни." },
            suggestAccept = { "$it мене влаштовує. Коли вам буде зручно забрати?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Добрий день! Велосипед ще продається?",
            sellerYes = "Вітаю! Так, продається.",
            buyerPaid = "Чудово. Я з іншого міста, тому вже оплатив через OLX Доставку.",
            buyerLink = "Це може тривати до 2 годин. Підтвердьте оплату тут, щоб отримати гроші: $ScamLink",
        ),
    )

    StoreLang.PL -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Rower miejski 28\"",
        guitar = "Gitara akustyczna Fender",
        chair = "Fotel biurowy vintage",
        camera = "Aparat Pentax Optio",
        lamp = "Lampka stołowa z abażurem",
        jacket = "Czerwona kurtka puchowa, M",
        coffee = "Ekspres Gastroback",
        guitarChat = GuitarChat(
            buyerAsks = "Dzień dobry! Czy gitara jest jeszcze dostępna?",
            sellerYes = "Dzień dobry! Tak, jest. Prawie nieużywana, bez rysek.",
            sellerCase = "Pokrowiec jest w zestawie, dorzucę też nowy komplet strun.",
            buyerOffer = { "Super. Czy możliwe jest $it, jeśli odbiorę dzisiaj?" },
            suggestKeep = { "Wolę zostać przy $it – w cenie pokrowiec i nowe struny." },
            suggestAccept = { "$it mi odpowiada. Kiedy możemy się umówić na odbiór?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Dzień dobry! Czy rower jest jeszcze na sprzedaż?",
            sellerYes = "Dzień dobry! Tak, jest.",
            buyerPaid = "Świetnie. Mieszkam w innym mieście, więc już zapłaciłem przez OLX Dostawę.",
            buyerLink = "To może potrwać do 2 godzin. Potwierdź płatność tutaj, aby otrzymać pieniądze: $ScamLink",
        ),
    )

    StoreLang.RO -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Bicicletă de oraș 28\"",
        guitar = "Chitară acustică Fender",
        chair = "Scaun de birou vintage",
        camera = "Aparat foto Pentax Optio",
        lamp = "Veioză cu abajur",
        jacket = "Geacă de puf roșie, M",
        coffee = "Espressor Gastroback",
        guitarChat = GuitarChat(
            buyerAsks = "Bună ziua! Mai este disponibilă chitara?",
            sellerYes = "Bună! Da, este. Aproape nefolosită, fără zgârieturi.",
            sellerCase = "Husa este inclusă și pot adăuga un set nou de corzi.",
            buyerOffer = { "Mulțumesc. Ați accepta $it dacă o ridic astăzi?" },
            suggestKeep = { "Prefer să rămân la $it, pentru că include husa și corzile noi." },
            suggestAccept = { "$it mi se potrivește. Când putem stabili ridicarea?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Bună ziua! Mai este bicicleta de vânzare?",
            sellerYes = "Bună! Da, este.",
            buyerPaid = "Perfect. Locuiesc în alt oraș, așa că am plătit deja prin Livrare OLX.",
            buyerLink = "Poate dura până la 2 ore. Confirmă plata aici ca să primești banii: $ScamLink",
        ),
    )

    StoreLang.BG -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Градски велосипед 28\"",
        guitar = "Акустична китара Fender",
        chair = "Ретро офис стол",
        camera = "Фотоапарат Pentax Optio",
        lamp = "Нощна лампа с абажур",
        jacket = "Червено пухено яке, M",
        coffee = "Кафемашина Gastroback",
        guitarChat = GuitarChat(
            buyerAsks = "Здравейте! Китарата още ли се продава?",
            sellerYes = "Здравейте! Да, продава се. Почти не е свирена, без драскотини.",
            sellerCase = "Калъфът е включен, а нов комплект струни ще добавя.",
            buyerOffer = { "Добре. Бихте ли я дали за $it, ако я взема днес?" },
            suggestKeep = { "Предпочитам да остана на $it — включени са калъф и нови струни." },
            suggestAccept = { "$it ме устройва. Кога можем да се уговорим за предаване?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Здравейте! Велосипедът още ли се продава?",
            sellerYes = "Здравейте! Да, продава се.",
            buyerPaid = "Супер. Живея в друг град, затова вече платих през OLX Доставка.",
            buyerLink = "Може да отнеме до 2 часа. Потвърдете плащането тук, за да получите парите: $ScamLink",
        ),
    )

    StoreLang.PT -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Bicicleta de cidade 28\"",
        guitar = "Guitarra acústica Fender",
        chair = "Cadeira de escritório vintage",
        camera = "Câmara Pentax Optio",
        lamp = "Candeeiro de mesa com abajur",
        jacket = "Casaco de penas vermelho, M",
        coffee = "Máquina de café Gastroback",
        guitarChat = GuitarChat(
            buyerAsks = "Olá! A guitarra ainda está disponível?",
            sellerYes = "Olá! Sim, está. Quase não foi usada, sem riscos.",
            sellerCase = "A bolsa está incluída e posso juntar um jogo de cordas novo.",
            buyerOffer = { "Combinado. Aceita $it se eu a levantar hoje?" },
            suggestKeep = { "Prefiro manter $it, já que inclui a bolsa e cordas novas." },
            suggestAccept = { "$it serve-me. Quando podemos combinar a entrega em mão?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Olá! A bicicleta ainda está à venda?",
            sellerYes = "Olá! Sim, está.",
            buyerPaid = "Ótimo. Moro noutra cidade, por isso já paguei através da Entrega OLX.",
            buyerLink = "Pode demorar até 2 horas. Confirme o pagamento aqui para receber o dinheiro: $ScamLink",
        ),
    )

    StoreLang.KK -> SellerDemo(
        account = "Alex",
        shop = "Retro Corner",
        bike = "Қалалық велосипед, 28\"",
        guitar = "Fender акустикалық гитарасы",
        chair = "Винтаж кеңсе орындығы",
        camera = "Pentax Optio фотоаппараты",
        lamp = "Абажурлы үстел шамы",
        jacket = "Қызыл пуховик, M",
        coffee = "Gastroback кофе машинасы",
        guitarChat = GuitarChat(
            buyerAsks = "Сәлеметсіз бе! Гитара әлі сатылымда ма?",
            sellerYes = "Сәлеметсіз бе! Иә, сатылымда. Дерлік ойналмаған, сызаты жоқ.",
            sellerCase = "Қапшығы бар, жаңа ішектер жинағын қосып беремін.",
            buyerOffer = { "Жақсы. Бүгін алып кетсем, баға $it болса қалай?" },
            suggestKeep = { "Бағасын $it деп қалдырғым келеді: қапшығы және жаңа ішектері бар." },
            suggestAccept = { "$it маған болады. Алып кету үшін қашан келісе аламыз?" },
        ),
        scamChat = ScamChat(
            buyerAsks = "Сәлеметсіз бе! Велосипед әлі сатылымда ма?",
            sellerYes = "Сәлеметсіз бе! Иә, сатылымда.",
            buyerPaid = "Керемет. Мен басқа қалада тұрамын, сондықтан OLX жеткізуі арқылы төлеп қойдым.",
            buyerLink = "Бұл 2 сағатқа дейін созылуы мүмкін. Ақшаңызды алу үшін төлемді осы жерде растаңыз: $ScamLink",
        ),
    )
}
