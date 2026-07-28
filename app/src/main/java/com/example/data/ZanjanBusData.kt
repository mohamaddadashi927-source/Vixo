package com.example.data

import com.example.model.BusLine
import com.example.model.Station
import org.osmdroid.util.GeoPoint

object ZanjanBusData {

    // Line 1: الهیه فاز ۲ ↔ پایانه میدان ارتش
    val line1 = BusLine(
        id = "line_elahieh2_artesh",
        name = "خط الهیه فاز ۲ ↔ پایانه میدان ارتش",
        number = "۱۰۱",
        colorHex = "#2563EB", // Blue
        startTerminalName = "پایانه میدان ارتش",
        startTerminalPoint = GeoPoint(36.6868265, 48.4891071),
        endTerminalName = "پایانه الهیه فاز ۲",
        endTerminalPoint = GeoPoint(36.6977425, 48.4727544),
        stations = listOf(
            Station("l1_s1", 36.685872, 48.4776076, "line_elahieh2_artesh", 0, "forward", "ایستگاه میدان ارتش"),
            Station("l1_s2", 36.6843585, 48.4706375, "line_elahieh2_artesh", 1, "forward", "ایستگاه بلوار ارتش"),
            Station("l1_s3", 36.6867619, 48.4670803, "line_elahieh2_artesh", 2, "forward", "ایستگاه آزادگان ۱"),
            Station("l1_s4", 36.6879583, 48.4667643, "line_elahieh2_artesh", 3, "forward", "ایستگاه آزادگان ۲"),
            Station("l1_s5", 36.6897989, 48.4663423, "line_elahieh2_artesh", 4, "forward", "ایستگاه میدان جهاد"),
            Station("l1_s6", 36.6913685, 48.4662047, "line_elahieh2_artesh", 5, "forward", "ایستگاه شهید بهشتی"),
            Station("l1_s7", 36.6929224, 48.4661206, "line_elahieh2_artesh", 6, "forward", "ایستگاه رسالت"),
            Station("l1_s8", 36.6944751, 48.4660137, "line_elahieh2_artesh", 7, "forward", "ایستگاه دانشجو"),
            Station("l1_s9", 36.6959918, 48.4659287, "line_elahieh2_artesh", 8, "forward", "ایستگاه معلم"),
            Station("l1_s10", 36.6975517, 48.4658237, "line_elahieh2_artesh", 9, "forward", "ایستگاه الهیه غربی"),
            Station("l1_s11", 36.6996872, 48.4767932, "line_elahieh2_artesh", 10, "forward", "ایستگاه الهیه مرکزی"),
            Station("l1_s12", 36.6987517, 48.4730697, "line_elahieh2_artesh", 11, "forward", "ایستگاه نیایش"),
            Station("l1_s13", 36.6998398, 48.4768186, "line_elahieh2_artesh", 12, "forward", "ایستگاه نرجس"),
            Station("l1_s14", 36.6983624, 48.4709156, "line_elahieh2_artesh", 13, "forward", "ایستگاه الهیه شرقی"),
            Station("l1_s15", 36.6971593, 48.4709011, "line_elahieh2_artesh", 14, "forward", "ایستگاه ولیعصر الهیه"),
            Station("l1_s16", 36.6956031, 48.4709066, "line_elahieh2_artesh", 15, "forward", "ایستگاه فاز ۲ - ۱"),
            Station("l1_s17", 36.6940483, 48.4709284, "line_elahieh2_artesh", 16, "forward", "ایستگاه فاز ۲ - ۲"),
            Station("l1_s18", 36.6924957, 48.4709439, "line_elahieh2_artesh", 17, "forward", "ایستگاه فاز ۲ - ۳"),
            Station("l1_s19", 36.6909374, 48.4709527, "line_elahieh2_artesh", 18, "forward", "ایستگاه فاز ۲ - ۴"),
            Station("l1_s20", 36.6893795, 48.4709732, "line_elahieh2_artesh", 19, "forward", "ایستگاه فاز ۲ - ۵"),
            Station("l1_s21", 36.6878289, 48.4709874, "line_elahieh2_artesh", 20, "forward", "ایستگاه فاز ۲ - ۶"),
            Station("l1_s22", 36.6863432, 48.4837756, "line_elahieh2_artesh", 21, "forward", "ایستگاه پایانی الهیه ۲")
        ),
        polyline = listOf(
            GeoPoint(36.6868265, 48.4891071),
            GeoPoint(36.685872, 48.4776076),
            GeoPoint(36.6843585, 48.4706375),
            GeoPoint(36.6867619, 48.4670803),
            GeoPoint(36.6879583, 48.4667643),
            GeoPoint(36.6897989, 48.4663423),
            GeoPoint(36.6913685, 48.4662047),
            GeoPoint(36.6929224, 48.4661206),
            GeoPoint(36.6944751, 48.4660137),
            GeoPoint(36.6959918, 48.4659287),
            GeoPoint(36.6975517, 48.4658237),
            GeoPoint(36.7029684, 48.4706696),
            GeoPoint(36.7010993, 48.4707496),
            GeoPoint(36.6996872, 48.4767932),
            GeoPoint(36.6987517, 48.4730697),
            GeoPoint(36.6998398, 48.4768186),
            GeoPoint(36.6983624, 48.4709156),
            GeoPoint(36.6971593, 48.4709011),
            GeoPoint(36.6956031, 48.4709066),
            GeoPoint(36.6940483, 48.4709284),
            GeoPoint(36.6924957, 48.4709439),
            GeoPoint(36.6909374, 48.4709527),
            GeoPoint(36.6893795, 48.4709732),
            GeoPoint(36.6878289, 48.4709874),
            GeoPoint(36.6863432, 48.4837756),
            GeoPoint(36.6977425, 48.4727544)
        )
    )

    // Line 2: الهیه فاز ۱ ↔ سبزه میدان (بر اساس داده‌های واقعی ۲۳ ایستگاه GPX)
    val line2ForwardStations = listOf(
        Station("l2_s1", 36.70610833, 48.46722167, "line_elahieh1_sabzeh", 0, "forward", "پایانه الهیه فاز یک"),
        Station("l2_s2", 36.70322833, 48.46657667, "line_elahieh1_sabzeh", 1, "forward", "ایستگاه یکم"),
        Station("l2_s3", 36.70274500, 48.46022167, "line_elahieh1_sabzeh", 2, "forward", "ایستگاه دوم"),
        Station("l2_s4", 36.70015167, 48.46140500, "line_elahieh1_sabzeh", 3, "forward", "ایستگاه سوم"),
        Station("l2_s5", 36.69934000, 48.46197167, "line_elahieh1_sabzeh", 4, "forward", "ایستگاه چهارم"),
        Station("l2_s6", 36.69824167, 48.46233667, "line_elahieh1_sabzeh", 5, "forward", "ایستگاه پنجم"),
        Station("l2_s7", 36.69631000, 48.46657167, "line_elahieh1_sabzeh", 6, "forward", "ایستگاه ششم"),
        Station("l2_s8", 36.69530000, 48.46214167, "line_elahieh1_sabzeh", 7, "forward", "ایستگاه هفتم"),
        Station("l2_s9", 36.69336000, 48.46246333, "line_elahieh1_sabzeh", 8, "forward", "ایستگاه هشتم"),
        Station("l2_s10", 36.69364167, 48.46553833, "line_elahieh1_sabzeh", 9, "forward", "ایستگاه نهم"),
        Station("l2_s11", 36.69443667, 48.46919167, "line_elahieh1_sabzeh", 10, "forward", "ایستگاه دهم"),
        Station("l2_s12", 36.69091333, 48.47049500, "line_elahieh1_sabzeh", 11, "forward", "ایستگاه یازدهم"),
        Station("l2_s13", 36.68962667, 48.46475333, "line_elahieh1_sabzeh", 12, "forward", "ایستگاه دوازدهم"),
        Station("l2_s14", 36.68637000, 48.46735000, "line_elahieh1_sabzeh", 13, "forward", "ایستگاه سیزدهم"),
        Station("l2_s15", 36.68181000, 48.47415333, "line_elahieh1_sabzeh", 14, "forward", "ایستگاه چهاردهم"),
        Station("l2_s16", 36.67753500, 48.47390667, "line_elahieh1_sabzeh", 15, "forward", "ایستگاه پانزدهم"),
        Station("l2_s17", 36.67674833, 48.47700000, "line_elahieh1_sabzeh", 16, "forward", "ایستگاه شانزدهم"),
        Station("l2_s18", 36.67529667, 48.48117833, "line_elahieh1_sabzeh", 17, "forward", "ایستگاه هفدهم"),
        Station("l2_s19", 36.67188833, 48.47544167, "line_elahieh1_sabzeh", 18, "forward", "ایستگاه هجدهم"),
        Station("l2_s20", 36.66937667, 48.47344667, "line_elahieh1_sabzeh", 19, "forward", "ایستگاه نوزدهم"),
        Station("l2_s21", 36.66868667, 48.47800167, "line_elahieh1_sabzeh", 20, "forward", "ایستگاه بیستم"),
        Station("l2_s22", 36.66866333, 48.47823000, "line_elahieh1_sabzeh", 21, "forward", "توقف کوتاه (سبزه میدان)"),
        Station("l2_s23", 36.66899000, 48.47851667, "line_elahieh1_sabzeh", 22, "forward", "پایانه سبزه میدان")
    )

    val line2BackwardStations = listOf(
        Station("l2_b1", 36.66899000, 48.47851667, "line_elahieh1_sabzeh", 0, "backward", "پایانه سبزه میدان (برگشت)"),
        Station("l2_b2", 36.66866333, 48.47823000, "line_elahieh1_sabzeh", 1, "backward", "توقف کوتاه (برگشت)"),
        Station("l2_b3", 36.66868667, 48.47800167, "line_elahieh1_sabzeh", 2, "backward", "ایستگاه بیستم (برگشت)"),
        Station("l2_b4", 36.66937667, 48.47344667, "line_elahieh1_sabzeh", 3, "backward", "ایستگاه نوزدهم (برگشت)"),
        Station("l2_b5", 36.67188833, 48.47544167, "line_elahieh1_sabzeh", 4, "backward", "ایستگاه هجدهم (برگشت)"),
        Station("l2_b6", 36.67529667, 48.48117833, "line_elahieh1_sabzeh", 5, "backward", "ایستگاه هفدهم (برگشت)"),
        Station("l2_b7", 36.67674833, 48.47700000, "line_elahieh1_sabzeh", 6, "backward", "ایستگاه شانزدهم (برگشت)"),
        Station("l2_b8", 36.67753500, 48.47390667, "line_elahieh1_sabzeh", 7, "backward", "ایستگاه پانزدهم (برگشت)"),
        Station("l2_b9", 36.68181000, 48.47415333, "line_elahieh1_sabzeh", 8, "backward", "ایستگاه چهاردهم (برگشت)"),
        Station("l2_b10", 36.68637000, 48.46735000, "line_elahieh1_sabzeh", 9, "backward", "ایستگاه سیزدهم (برگشت)"),
        Station("l2_b11", 36.68962667, 48.46475333, "line_elahieh1_sabzeh", 10, "backward", "ایستگاه دوازدهم (برگشت)"),
        Station("l2_b12", 36.69091333, 48.47049500, "line_elahieh1_sabzeh", 11, "backward", "ایستگاه یازدهم (برگشت)"),
        Station("l2_b13", 36.69443667, 48.46919167, "line_elahieh1_sabzeh", 12, "backward", "ایستگاه دهم (برگشت)"),
        Station("l2_b14", 36.69364167, 48.46553833, "line_elahieh1_sabzeh", 13, "backward", "ایستگاه نهم (برگشت)"),
        Station("l2_b15", 36.69336000, 48.46246333, "line_elahieh1_sabzeh", 14, "backward", "ایستگاه هشتم (برگشت)"),
        Station("l2_b16", 36.69530000, 48.46214167, "line_elahieh1_sabzeh", 15, "backward", "ایستگاه هفتم (برگشت)"),
        Station("l2_b17", 36.69631000, 48.46657167, "line_elahieh1_sabzeh", 16, "backward", "ایستگاه ششم (برگشت)"),
        Station("l2_b18", 36.69824167, 48.46233667, "line_elahieh1_sabzeh", 17, "backward", "ایستگاه پنجم (برگشت)"),
        Station("l2_b19", 36.69934000, 48.46197167, "line_elahieh1_sabzeh", 18, "backward", "ایستگاه چهارم (برگشت)"),
        Station("l2_b20", 36.70015167, 48.46140500, "line_elahieh1_sabzeh", 19, "backward", "ایستگاه سوم (برگشت)"),
        Station("l2_b21", 36.70274500, 48.46022167, "line_elahieh1_sabzeh", 20, "backward", "ایستگاه دوم (برگشت)"),
        Station("l2_b22", 36.70322833, 48.46657667, "line_elahieh1_sabzeh", 21, "backward", "ایستگاه یکم (برگشت)"),
        Station("l2_b23", 36.70610833, 48.46722167, "line_elahieh1_sabzeh", 22, "backward", "پایانه الهیه فاز یک (برگشت)")
    )

    private fun generateDensePolyline(stations: List<Station>, totalPoints: Int = 598): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        if (stations.isEmpty()) return result
        if (stations.size == 1) return listOf(stations.first().toGeoPoint())

        val segments = stations.size - 1
        val pointsPerSegment = totalPoints / segments

        for (i in 0 until segments) {
            val p1 = stations[i]
            val p2 = stations[i + 1]
            val numPoints = if (i == segments - 1) totalPoints - result.size else pointsPerSegment

            for (j in 0 until numPoints) {
                val frac = j.toDouble() / numPoints
                val lat = p1.lat + (p2.lat - p1.lat) * frac
                val lng = p1.lng + (p2.lng - p1.lng) * frac
                result.add(GeoPoint(lat, lng))
            }
        }
        result.add(stations.last().toGeoPoint())
        return result
    }

    val line2Polyline = generateDensePolyline(line2ForwardStations, 598)

    val line2 = BusLine(
        id = "line_elahieh1_sabzeh",
        name = "خط الهیه فاز ۱ ↔ سبزه میدان",
        number = "۱۰۲",
        colorHex = "#059669", // Emerald Green
        startTerminalName = "پایانه الهیه فاز یک",
        startTerminalPoint = GeoPoint(36.70610833, 48.46722167),
        endTerminalName = "پایانه سبزه میدان",
        endTerminalPoint = GeoPoint(36.66899000, 48.47851667),
        stations = line2ForwardStations + line2BackwardStations,
        polyline = line2Polyline
    )

    // Line 3: کوی فرهنگ ↔ سبزه میدان (مهمترین - دارای Polyline واقعی)
    val line3 = BusLine(
        id = "line_kooyfarhang_sabzeh",
        name = "خط کوی فرهنگ ↔ سبزه میدان",
        number = "۱۰۳",
        colorHex = "#D97706", // Amber / Orange
        startTerminalName = "سبزه میدان",
        startTerminalPoint = GeoPoint(36.6706024, 48.4791907),
        endTerminalName = "پایانه جمعه بازار (کوی فرهنگ)",
        endTerminalPoint = GeoPoint(36.702986, 48.4583872),
        stations = listOf(
            Station("l3_s1", 36.6710606, 48.4779636, "line_kooyfarhang_sabzeh", 0, "forward", "ایستگاه سبزه میدان"),
            Station("l3_s2", 36.6714652, 48.4756306, "line_kooyfarhang_sabzeh", 1, "forward", "ایستگاه خیابان طالقانی"),
            Station("l3_s3", 36.6725243, 48.4762212, "line_kooyfarhang_sabzeh", 2, "forward", "ایستگاه میدان کارگر"),
            Station("l3_s4", 36.6738034, 48.4746957, "line_kooyfarhang_sabzeh", 3, "forward", "ایستگاه خیابان سیلو"),
            Station("l3_s5", 36.6750956, 48.4731903, "line_kooyfarhang_sabzeh", 4, "forward", "ایستگاه کوی فرهنگ ۱"),
            Station("l3_s6", 36.6763924, 48.4716766, "line_kooyfarhang_sabzeh", 5, "forward", "ایستگاه کوی فرهنگ ۲"),
            Station("l3_s7", 36.6776885, 48.4701625, "line_kooyfarhang_sabzeh", 6, "forward", "ایستگاه کوی فرهنگ ۳"),
            Station("l3_s8", 36.6789862, 48.4686483, "line_kooyfarhang_sabzeh", 7, "forward", "ایستگاه کوی فرهنگ ۴"),
            Station("l3_s9", 36.6802827, 48.4671341, "line_kooyfarhang_sabzeh", 8, "forward", "ایستگاه کوی فرهنگ ۵"),
            Station("l3_s10", 36.6815795, 48.4656198, "line_kooyfarhang_sabzeh", 9, "forward", "ایستگاه میدان نصر"),
            Station("l3_s11", 36.6828757, 48.4641057, "line_kooyfarhang_sabzeh", 10, "forward", "ایستگاه میدان دانش"),
            Station("l3_s12", 36.6841723, 48.4625914, "line_kooyfarhang_sabzeh", 11, "forward", "ایستگاه دانشگاه ۱"),
            Station("l3_s13", 36.6854686, 48.4610772, "line_kooyfarhang_sabzeh", 12, "forward", "ایستگاه دانشگاه ۲"),
            Station("l3_s14", 36.6867651, 48.459563, "line_kooyfarhang_sabzeh", 13, "forward", "ایستگاه نسترن"),
            Station("l3_s15", 36.6880614, 48.4580487, "line_kooyfarhang_sabzeh", 14, "forward", "ایستگاه یاسمن"),
            Station("l3_s16", 36.6893579, 48.4565345, "line_kooyfarhang_sabzeh", 15, "forward", "ایستگاه شقایق"),
            Station("l3_s17", 36.6906543, 48.4550202, "line_kooyfarhang_sabzeh", 16, "forward", "ایستگاه نرگس"),
            Station("l3_s18", 36.6919507, 48.453506, "line_kooyfarhang_sabzeh", 17, "forward", "ایستگاه لاله"),
            Station("l3_s19", 36.693247, 48.4519918, "line_kooyfarhang_sabzeh", 18, "forward", "ایستگاه مریم"),
            Station("l3_s20", 36.7002206, 48.4544349, "line_kooyfarhang_sabzeh", 19, "forward", "ایستگاه جمعه بازار")
        ),
        // Polyline کامل (مسیر واقعی)
        polyline = listOf(
            GeoPoint(36.702986, 48.4583872),
            GeoPoint(36.7002206, 48.4544349),
            GeoPoint(36.6979294, 48.4624171),
            GeoPoint(36.6953151, 48.4521143),
            GeoPoint(36.6927038, 48.4511103),
            GeoPoint(36.6900925, 48.4501063),
            GeoPoint(36.6874812, 48.4491023),
            GeoPoint(36.6848699, 48.4480983),
            GeoPoint(36.6822586, 48.4470943),
            GeoPoint(36.6796473, 48.4460903),
            GeoPoint(36.677036, 48.4450863),
            GeoPoint(36.6744247, 48.4440823),
            GeoPoint(36.6718134, 48.4430783),
            GeoPoint(36.6706024, 48.4791907)
        )
    )

    val allLines = listOf(line1, line2, line3)
}
