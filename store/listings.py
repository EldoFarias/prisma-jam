"""Store listing texts and screenshot captions in the 8 languages of Prisma Jam.

Writes store/listings/<play-locale>/{title,short_description,full_description}.txt and checks the
Play Console limits (30 / 80 / 4000 characters). Captions are for the store screenshots.

    python3 store/listings.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

L = {}

L["en-US"] = dict(
    title="Prisma Jam: Jelly Block Puzzle",
    short="Slide jelly blocks to the gate of their color and watch them burst into glass!",
    full="""Slide the glossy jelly blocks to the gate of the same color and watch them burst into a shower of glass. Prisma Jam is an elegant sliding puzzle: easy to start, hard to master, with a clock ticking.

★ A PUZZLE THAT MAKES YOU THINK
• Every block needs a free path to its gate: find the right order
• Only a few moves are possible at a time: plan ahead
• A hard level every 10 levels, then a breather
• Endless levels, every one different, every one solvable

★ NEW TWISTS AS YOU PLAY
• Stones that never move
• Frozen blocks that melt as others leave
• One-way blocks with arrows
• Two-layer blocks: break the shell, then find the new color
• Keys and padlocks
• Gates that open and close on a timer

★ POWER-UPS
• Hint: shows the block that can leave now
• Freeze: stops the clock for 10 seconds
• Hammer: smashes any block
• Scissors: cuts a block in two

★ EVERY DAY
• Daily challenge with a streak to keep alive
• Three daily missions and a daily chest that grows over 7 days
• Level map with worlds, stars and chests along the trail

★ MADE TO FEEL GOOD
• Glossy 3D jelly blocks and a premium neon look
• Satisfying glass-burst effect every time a block leaves
• 6 themes: Neon, Ocean, Candy, Lava, Galaxy and Gold
• Relaxing music, sounds and vibration (all optional)
• Works offline

Free to play, with ads. No account needed.
""",
    captions=["Slide blocks to their gate", "Burst them into glass", "Plan the right order",
              "New twists as you play", "Power-ups when you need them", "Daily challenge and map", "6 beautiful themes"],
)

L["pt-BR"] = dict(
    title="Prisma Jam: Blocos de Cores",
    short="Deslize os blocos de gelatina até a porta da mesma cor e veja-os virar vidro!",
    full="""Deslize os blocos de gelatina brilhantes até a porta da mesma cor e veja cada um explodir numa chuva de vidro. Prisma Jam é um quebra-cabeça elegante: fácil de começar, difícil de dominar, com o relógio correndo.

★ UM QUEBRA-CABEÇA QUE FAZ PENSAR
• Cada bloco precisa de caminho livre até a sua porta: descubra a ordem certa
• Só alguns movimentos são possíveis por vez: planeje antes
• Uma fase difícil a cada 10 fases, e depois um respiro
• Fases infinitas, todas diferentes, todas com solução

★ NOVIDADES PELO CAMINHO
• Pedras que nunca se movem
• Blocos congelados que derretem quando outros saem
• Blocos de direção, com setas
• Blocos de duas camadas: quebre a casca e encontre a nova cor
• Chaves e cadeados
• Portas que abrem e fecham com o tempo

★ PODERES
• Dica: mostra o bloco que pode sair agora
• Congelar: para o relógio por 10 segundos
• Martelo: quebra qualquer bloco
• Tesoura: corta um bloco em dois

★ TODO DIA
• Desafio diário com sequência de dias para manter
• Três missões diárias e um baú diário que cresce ao longo de 7 dias
• Mapa de fases com mundos, estrelas e baús pelo caminho

★ FEITO PARA SER GOSTOSO DE JOGAR
• Blocos de gelatina 3D brilhantes e visual neon premium
• Efeito de vidro estilhaçando toda vez que um bloco sai
• 6 temas: Neon, Oceano, Doces, Lava, Galáxia e Ouro
• Música relaxante, sons e vibração (tudo opcional)
• Funciona sem internet

Gratuito, com anúncios. Não precisa de conta.
""",
    captions=["Leve cada bloco à sua porta", "Veja-os virar vidro", "Descubra a ordem certa",
              "Novidades pelo caminho", "Poderes quando precisar", "Desafio diário e mapa", "6 temas lindos"],
)

L["es-ES"] = dict(
    title="Prisma Jam: Bloques de Color",
    short="Desliza bloques de gelatina a la puerta de su color y míralos hacerse cristal.",
    full="""Desliza los brillantes bloques de gelatina hasta la puerta del mismo color y mira cómo estallan en una lluvia de cristal. Prisma Jam es un rompecabezas elegante: fácil de empezar, difícil de dominar, con el reloj en marcha.

★ UN ROMPECABEZAS QUE HACE PENSAR
• Cada bloque necesita un camino libre hasta su puerta: descubre el orden correcto
• Solo hay unos pocos movimientos posibles a la vez: planifica
• Un nivel difícil cada 10 niveles, y luego un respiro
• Niveles infinitos, todos distintos, todos con solución

★ NOVEDADES POR EL CAMINO
• Piedras que nunca se mueven
• Bloques congelados que se derriten cuando salen otros
• Bloques de dirección, con flechas
• Bloques de dos capas: rompe la cáscara y busca el nuevo color
• Llaves y candados
• Puertas que se abren y se cierran con el tiempo

★ PODERES
• Pista: muestra el bloque que puede salir ahora
• Congelar: detiene el reloj 10 segundos
• Martillo: rompe cualquier bloque
• Tijeras: corta un bloque en dos

★ CADA DÍA
• Desafío diario con racha de días para mantener
• Tres misiones diarias y un cofre diario que crece durante 7 días
• Mapa de niveles con mundos, estrellas y cofres por el camino

★ HECHO PARA DISFRUTAR
• Bloques de gelatina 3D brillantes y un look neón premium
• Efecto de cristal que estalla cada vez que sale un bloque
• 6 temas: Neón, Océano, Dulces, Lava, Galaxia y Oro
• Música relajante, sonidos y vibración (todo opcional)
• Funciona sin internet

Gratis, con anuncios. No necesitas cuenta.
""",
    captions=["Lleva cada bloque a su puerta", "Míralos hacerse cristal", "Descubre el orden correcto",
              "Novedades por el camino", "Poderes cuando los necesites", "Desafío diario y mapa", "6 temas preciosos"],
)

L["fr-FR"] = dict(
    title="Prisma Jam : Blocs de Couleur",
    short="Glisse les blocs de gelée vers la porte de leur couleur et vois-les éclater !",
    full="""Fais glisser les blocs de gelée brillants vers la porte de la même couleur et regarde-les éclater en pluie de verre. Prisma Jam est un puzzle élégant : facile à commencer, difficile à maîtriser, avec le chrono qui tourne.

★ UN PUZZLE QUI FAIT RÉFLÉCHIR
• Chaque bloc a besoin d'un chemin libre jusqu'à sa porte : trouve le bon ordre
• Seuls quelques coups sont possibles à la fois : anticipe
• Un niveau difficile tous les 10 niveaux, puis une pause
• Niveaux infinis, tous différents, tous avec une solution

★ DES NOUVEAUTÉS EN CHEMIN
• Des pierres qui ne bougent jamais
• Des blocs gelés qui fondent quand d'autres sortent
• Des blocs à sens unique, avec des flèches
• Des blocs à deux couches : casse la coque et trouve la nouvelle couleur
• Des clés et des cadenas
• Des portes qui s'ouvrent et se ferment avec le temps

★ BONUS
• Indice : montre le bloc qui peut sortir maintenant
• Gel : arrête le chrono 10 secondes
• Marteau : casse n'importe quel bloc
• Ciseaux : coupe un bloc en deux

★ CHAQUE JOUR
• Défi du jour avec une série de jours à garder
• Trois missions par jour et un coffre quotidien qui grandit sur 7 jours
• Carte des niveaux avec mondes, étoiles et coffres en chemin

★ FAIT POUR FAIRE DU BIEN
• Blocs de gelée 3D brillants et look néon premium
• Effet de verre qui éclate à chaque bloc qui sort
• 6 thèmes : Néon, Océan, Bonbons, Lave, Galaxie et Or
• Musique relaxante, sons et vibrations (tout est optionnel)
• Fonctionne hors ligne

Gratuit, avec publicités. Aucun compte nécessaire.
""",
    captions=["Chaque bloc vers sa porte", "Regarde-les éclater", "Trouve le bon ordre",
              "Des nouveautés en chemin", "Des bonus quand il faut", "Défi du jour et carte", "6 jolis thèmes"],
)

L["de-DE"] = dict(
    title="Prisma Jam: Farbblock-Puzzle",
    short="Schiebe Geleeblöcke zum Tor ihrer Farbe und sieh sie zu Glas zerspringen!",
    full="""Schiebe die glänzenden Geleeblöcke zum Tor derselben Farbe und sieh zu, wie sie in einem Glasregen zerspringen. Prisma Jam ist ein elegantes Schiebepuzzle: leicht zu lernen, schwer zu meistern, mit tickender Uhr.

★ EIN PUZZLE ZUM NACHDENKEN
• Jeder Block braucht einen freien Weg zu seinem Tor: finde die richtige Reihenfolge
• Nur wenige Züge sind gleichzeitig möglich: plane voraus
• Alle 10 Level ein schweres Level, danach eine Verschnaufpause
• Endlose Level, alle verschieden, alle lösbar

★ NEUES UNTERWEGS
• Steine, die sich nie bewegen
• Gefrorene Blöcke, die auftauen, wenn andere gehen
• Richtungsblöcke mit Pfeilen
• Zweischichtige Blöcke: Hülle knacken, neue Farbe finden
• Schlüssel und Schlösser
• Tore, die sich mit der Zeit öffnen und schließen

★ POWER-UPS
• Tipp: zeigt den Block, der jetzt rausgehen kann
• Einfrieren: stoppt die Uhr für 10 Sekunden
• Hammer: zerschlägt jeden Block
• Schere: schneidet einen Block in zwei Teile

★ JEDEN TAG
• Tägliche Challenge mit einer Serie zum Halten
• Drei tägliche Missionen und eine Tagestruhe, die über 7 Tage wächst
• Levelkarte mit Welten, Sternen und Truhen am Weg

★ ZUM WOHLFÜHLEN GEMACHT
• Glänzende 3D-Geleeblöcke und ein edler Neon-Look
• Glas-Effekt, jedes Mal wenn ein Block rausgeht
• 6 Themen: Neon, Ozean, Süßigkeiten, Lava, Galaxie und Gold
• Entspannende Musik, Sounds und Vibration (alles optional)
• Funktioniert offline

Kostenlos, mit Werbung. Kein Konto nötig.
""",
    captions=["Jeder Block zu seinem Tor", "Sieh sie zu Glas zerspringen", "Finde die richtige Reihenfolge",
              "Neues unterwegs", "Power-ups, wenn du sie brauchst", "Tägliche Challenge und Karte", "6 schöne Themen"],
)

L["tr-TR"] = dict(
    title="Prisma Jam: Renkli Blok",
    short="Jöle blokları kendi renklerindeki kapıya kaydır ve cam gibi patlamalarını izle!",
    full="""Parlak jöle blokları aynı renkteki kapıya kaydır ve cam yağmuruna dönüşmelerini izle. Prisma Jam zarif bir kaydırma bulmacası: başlaması kolay, ustalaşması zor ve saat işliyor.

★ DÜŞÜNDÜREN BİR BULMACA
• Her bloğun kapısına kadar boş bir yolu olmalı: doğru sırayı bul
• Aynı anda sadece birkaç hamle mümkün: önceden planla
• Her 10 bölümde bir zor bölüm, sonra bir nefes molası
• Sonsuz bölüm, hepsi farklı, hepsi çözülebilir

★ YOL BOYUNCA YENİLİKLER
• Asla hareket etmeyen taşlar
• Diğerleri çıktıkça eriyen donmuş bloklar
• Oklu, tek yönlü bloklar
• İki katmanlı bloklar: kabuğu kır, yeni rengi bul
• Anahtarlar ve kilitler
• Zamanla açılıp kapanan kapılar

★ GÜÇLER
• İpucu: şimdi çıkabilecek bloğu gösterir
• Dondur: saati 10 saniye durdurur
• Çekiç: herhangi bir bloğu kırar
• Makas: bir bloğu ikiye böler

★ HER GÜN
• Serini korumak için günlük görev
• Üç günlük görev ve 7 gün boyunca büyüyen günlük sandık
• Dünyalar, yıldızlar ve yol sandıklarıyla bölüm haritası

★ KEYİF VERMEK İÇİN
• Parlak 3D jöle bloklar ve premium neon görünüm
• Her blok çıktığında cam patlama efekti
• 6 tema: Neon, Okyanus, Şeker, Lav, Galaksi ve Altın
• Rahatlatıcı müzik, sesler ve titreşim (hepsi isteğe bağlı)
• İnternetsiz çalışır

Ücretsiz, reklamlı. Hesap gerekmez.
""",
    captions=["Her bloğu kapısına götür", "Cam gibi patlamalarını izle", "Doğru sırayı bul",
              "Yol boyunca yenilikler", "Gerektiğinde güçler", "Günlük görev ve harita", "6 güzel tema"],
)

L["id"] = dict(
    title="Prisma Jam: Puzzle Blok Warna",
    short="Geser blok jeli ke gerbang warnanya dan lihat mereka pecah jadi kaca!",
    full="""Geser blok jeli yang berkilau ke gerbang dengan warna yang sama dan lihat mereka pecah jadi hujan kaca. Prisma Jam adalah puzzle geser yang elegan: mudah dimulai, sulit dikuasai, dengan waktu yang terus berjalan.

★ PUZZLE YANG BIKIN BERPIKIR
• Setiap blok butuh jalan bebas ke gerbangnya: temukan urutan yang tepat
• Hanya sedikit langkah yang mungkin dalam satu waktu: rencanakan dulu
• Level sulit setiap 10 level, lalu level santai
• Level tanpa akhir, semuanya berbeda, semuanya bisa diselesaikan

★ KEJUTAN BARU DI SEPANJANG JALAN
• Batu yang tidak pernah bergerak
• Blok beku yang mencair saat blok lain keluar
• Blok satu arah dengan panah
• Blok dua lapis: pecahkan cangkangnya, temukan warna barunya
• Kunci dan gembok
• Gerbang yang membuka dan menutup sesuai waktu

★ KEKUATAN
• Petunjuk: menunjukkan blok yang bisa keluar sekarang
• Bekukan: menghentikan waktu selama 10 detik
• Palu: menghancurkan blok mana pun
• Gunting: memotong blok jadi dua

★ SETIAP HARI
• Tantangan harian dengan rangkaian hari yang harus dijaga
• Tiga misi harian dan peti harian yang bertambah selama 7 hari
• Peta level dengan dunia, bintang, dan peti di sepanjang jalan

★ DIBUAT AGAR MENYENANGKAN
• Blok jeli 3D berkilau dan tampilan neon premium
• Efek kaca pecah setiap kali blok keluar
• 6 tema: Neon, Samudra, Permen, Lava, Galaksi, dan Emas
• Musik santai, suara, dan getaran (semua opsional)
• Bisa dimainkan tanpa internet

Gratis, dengan iklan. Tidak perlu akun.
""",
    captions=["Bawa blok ke gerbangnya", "Lihat mereka pecah jadi kaca", "Temukan urutan yang tepat",
              "Kejutan baru di jalan", "Kekuatan saat dibutuhkan", "Tantangan harian dan peta", "6 tema cantik"],
)

L["hi-IN"] = dict(
    title="Prisma Jam: रंगीन ब्लॉक पज़ल",
    short="जेली ब्लॉक को उनके रंग के गेट तक खिसकाएँ और उन्हें काँच की तरह टूटते देखें!",
    full="""चमकदार जेली ब्लॉक को उसी रंग के गेट तक खिसकाएँ और उन्हें काँच की बारिश में टूटते देखें। Prisma Jam एक शानदार स्लाइडिंग पज़ल है: शुरू करना आसान, महारत पाना मुश्किल, और घड़ी चलती रहती है।

★ सोचने पर मजबूर करने वाली पज़ल
• हर ब्लॉक को अपने गेट तक खाली रास्ता चाहिए: सही क्रम खोजें
• एक समय में बस कुछ ही चालें संभव हैं: पहले से योजना बनाएँ
• हर 10 लेवल पर एक मुश्किल लेवल, फिर थोड़ा आराम
• अनंत लेवल, सब अलग, सबका हल मौजूद

★ रास्ते में नई चीज़ें
• कभी न हिलने वाले पत्थर
• जमे हुए ब्लॉक जो दूसरों के निकलने पर पिघलते हैं
• तीर वाले एक-दिशा ब्लॉक
• दो परत वाले ब्लॉक: खोल तोड़ें, नया रंग खोजें
• चाबियाँ और ताले
• समय के साथ खुलने और बंद होने वाले गेट

★ पावर-अप
• संकेत: अभी निकल सकने वाला ब्लॉक दिखाता है
• फ़्रीज़: घड़ी को 10 सेकंड रोकता है
• हथौड़ा: कोई भी ब्लॉक तोड़ता है
• कैंची: एक ब्लॉक को दो हिस्सों में काटती है

★ हर दिन
• रोज़ाना चुनौती और लगातार दिनों की स्ट्रीक
• तीन रोज़ाना मिशन और 7 दिनों में बढ़ने वाला रोज़ाना खज़ाना
• दुनियाओं, सितारों और रास्ते के खज़ानों वाला लेवल मैप

★ मज़ेदार बनाने के लिए
• चमकदार 3D जेली ब्लॉक और प्रीमियम नियॉन लुक
• हर ब्लॉक निकलने पर काँच टूटने का इफ़ेक्ट
• 6 थीम: नियॉन, समुद्र, कैंडी, लावा, गैलेक्सी और सोना
• सुकून भरा संगीत, आवाज़ें और वाइब्रेशन (सब वैकल्पिक)
• बिना इंटरनेट के चलता है

मुफ़्त, विज्ञापनों के साथ। किसी अकाउंट की ज़रूरत नहीं।
""",
    captions=["हर ब्लॉक को उसके गेट तक", "उन्हें काँच की तरह टूटते देखें", "सही क्रम खोजें",
              "रास्ते में नई चीज़ें", "ज़रूरत पर पावर-अप", "रोज़ाना चुनौती और मैप", "6 सुंदर थीम"],
)

# Emulator locale used for each listing's screenshots.
APP_LOCALE = {"en-US": "en-US", "pt-BR": "pt-BR", "es-ES": "es-ES", "fr-FR": "fr-FR", "de-DE": "de-DE",
              "tr-TR": "tr-TR", "id": "id-ID", "hi-IN": "hi-IN"}

if __name__ == "__main__":
    for loc, t in L.items():
        for key, limit in (("title", 30), ("short", 80), ("full", 4000)):
            n = len(t[key].strip())
            assert n <= limit, (loc, key, n, limit)
        assert len(t["captions"]) == 7, loc
        d = os.path.join(HERE, "listings", loc)
        os.makedirs(d, exist_ok=True)
        for key, name in (("title", "title"), ("short", "short_description"), ("full", "full_description")):
            with open(os.path.join(d, name + ".txt"), "w", encoding="utf-8", newline="\n") as f:
                f.write(t[key].strip() + "\n")
        print(f"{loc:6} title={len(t['title']):2} short={len(t['short']):2} full={len(t['full'].strip()):4}")
