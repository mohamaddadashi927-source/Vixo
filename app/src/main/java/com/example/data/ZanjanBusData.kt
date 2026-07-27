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

    // Line 2: الهیه فاز ۱ ↔ سبزه میدان (بر اساس داده‌های واقعی ۲۲ ایستگاه)
    val line2 = BusLine(
        id = "line_elahieh1_sabzeh",
        name = "خط الهیه فاز ۱ ↔ سبزه میدان",
        number = "۱۰۲",
        colorHex = "#059669", // Emerald Green
        startTerminalName = "ایستگاه ۱: مبدا - الهیه فاز یک",
        startTerminalPoint = GeoPoint(36.312500, 59.482100),
        endTerminalName = "ایستگاه ۲۲: مقصد - سبزه میدان",
        endTerminalPoint = GeoPoint(36.339800, 59.516900),
        stations = listOf(
            // Forward Stations (الهیه فاز یک به سمت پایانه سبزه میدان)
            Station("l2_s1", 36.312500, 59.482100, "line_elahieh1_sabzeh", 0, "forward", "ایستگاه ۱: مبدا - الهیه فاز یک"),
            Station("l2_s2", 36.313800, 59.483900, "line_elahieh1_sabzeh", 1, "forward", "ایستگاه ۲: بلوار الهیه - نبش سجادیه"),
            Station("l2_s3", 36.315100, 59.485600, "line_elahieh1_sabzeh", 2, "forward", "ایستگاه ۳: بلوار اقدسیه ۱۰"),
            Station("l2_s4", 36.316400, 59.487200, "line_elahieh1_sabzeh", 3, "forward", "ایستگاه ۴: میدان محمدیه"),
            Station("l2_s5", 36.317700, 59.488900, "line_elahieh1_sabzeh", 4, "forward", "ایستگاه ۵: تقاطع رحمانی"),
            Station("l2_s6", 36.319000, 59.490500, "line_elahieh1_sabzeh", 5, "forward", "ایستگاه ۶: بلوار صادقیه"),
            Station("l2_s7", 36.320300, 59.492100, "line_elahieh1_sabzeh", 6, "forward", "ایستگاه ۷: میدان نمایشگاه"),
            Station("l2_s8", 36.321600, 59.493800, "line_elahieh1_sabzeh", 7, "forward", "ایستگاه ۸: بلوار استقلال - تقاطع مانا"),
            Station("l2_s9", 36.322900, 59.495400, "line_elahieh1_sabzeh", 8, "forward", "ایستگاه ۹: میدان استقلال"),
            Station("l2_s10", 36.324200, 59.497100, "line_elahieh1_sabzeh", 9, "forward", "ایستگاه ۱۰: بزرگراه آزادی - پل روگذر"),
            Station("l2_s11", 36.325500, 59.498700, "line_elahieh1_sabzeh", 10, "forward", "ایستگاه ۱۱: تقاطع ایستگاه مترو صدف"),
            Station("l2_s12", 36.326800, 59.500400, "line_elahieh1_sabzeh", 11, "forward", "ایستگاه ۱۲: بلوار معلم - نبش معلم ۴۵"),
            Station("l2_s13", 36.328100, 59.502000, "line_elahieh1_sabzeh", 12, "forward", "ایستگاه ۱۳: سه راه دانش‌آموز"),
            Station("l2_s14", 36.329400, 59.503700, "line_elahieh1_sabzeh", 13, "forward", "ایستگاه ۱۴: میدان جهاد"),
            Station("l2_s15", 36.330700, 59.505300, "line_elahieh1_sabzeh", 14, "forward", "ایستگاه ۱۵: چهارراه ابوطالب"),
            Station("l2_s16", 36.332000, 59.507000, "line_elahieh1_sabzeh", 15, "forward", "ایستگاه ۱۶: میدان شهید دیلمی"),
            Station("l2_s17", 36.333300, 59.508600, "line_elahieh1_sabzeh", 16, "forward", "ایستگاه ۱۷: خیابان دانشگاه"),
            Station("l2_s18", 36.334600, 59.510300, "line_elahieh1_sabzeh", 17, "forward", "ایستگاه ۱۸: میدان شهدا"),
            Station("l2_s19", 36.335900, 59.511900, "line_elahieh1_sabzeh", 18, "forward", "ایستگاه ۱۹: خیابان خسروی نو"),
            Station("l2_s20", 36.337200, 59.513600, "line_elahieh1_sabzeh", 19, "forward", "ایستگاه ۲۰: فلکه آب"),
            Station("l2_s21", 36.338500, 59.515200, "line_elahieh1_sabzeh", 20, "forward", "ایستگاه ۲۱: بازار مرکزی"),
            Station("l2_s22", 36.339800, 59.516900, "line_elahieh1_sabzeh", 21, "forward", "ایستگاه ۲۲: مقصد - سبزه میدان"),

            // Backward Stations (پایانه سبزه میدان به سمت الهیه فاز یک)
            Station("l2_b1", 36.339800, 59.516900, "line_elahieh1_sabzeh", 0, "backward", "ایستگاه ۱ (برگشت): مبدا - سبزه میدان"),
            Station("l2_b2", 36.338500, 59.515200, "line_elahieh1_sabzeh", 1, "backward", "ایستگاه ۲ (برگشت): بازار مرکزی"),
            Station("l2_b3", 36.337200, 59.513600, "line_elahieh1_sabzeh", 2, "backward", "ایستگاه ۳ (برگشت): فلکه آب"),
            Station("l2_b4", 36.335900, 59.511900, "line_elahieh1_sabzeh", 3, "backward", "ایستگاه ۴ (برگشت): خیابان خسروی نو"),
            Station("l2_b5", 36.334600, 59.510300, "line_elahieh1_sabzeh", 4, "backward", "ایستگاه ۵ (برگشت): میدان شهدا"),
            Station("l2_b6", 36.333300, 59.508600, "line_elahieh1_sabzeh", 5, "backward", "ایستگاه ۶ (برگشت): خیابان دانشگاه"),
            Station("l2_b7", 36.332000, 59.507000, "line_elahieh1_sabzeh", 6, "backward", "ایستگاه ۷ (برگشت): میدان شهید دیلمی"),
            Station("l2_b8", 36.330700, 59.505300, "line_elahieh1_sabzeh", 7, "backward", "ایستگاه ۸ (برگشت): چهارراه ابوطالب"),
            Station("l2_b9", 36.329400, 59.503700, "line_elahieh1_sabzeh", 8, "backward", "ایستگاه ۹ (برگشت): میدان جهاد"),
            Station("l2_b10", 36.328100, 59.502000, "line_elahieh1_sabzeh", 9, "backward", "ایستگاه ۱۰ (برگشت): سه راه دانش‌آموز"),
            Station("l2_b11", 36.326800, 59.500400, "line_elahieh1_sabzeh", 10, "backward", "ایستگاه ۱۱ (برگشت): بلوار معلم - نبش معلم ۴۵"),
            Station("l2_b12", 36.325500, 59.498700, "line_elahieh1_sabzeh", 11, "backward", "ایستگاه ۱۲ (برگشت): تقاطع ایستگاه مترو صدف"),
            Station("l2_b13", 36.324200, 59.497100, "line_elahieh1_sabzeh", 12, "backward", "ایستگاه ۱۳ (برگشت): بزرگراه آزادی - پل روگذر"),
            Station("l2_b14", 36.322900, 59.495400, "line_elahieh1_sabzeh", 13, "backward", "ایستگاه ۱۴ (برگشت): میدان استقلال"),
            Station("l2_b15", 36.321600, 59.493800, "line_elahieh1_sabzeh", 14, "backward", "ایستگاه ۱۵ (برگشت): بلوار استقلال - تقاطع مانا"),
            Station("l2_b16", 36.320300, 59.492100, "line_elahieh1_sabzeh", 15, "backward", "ایستگاه ۱۶ (برگشت): میدان نمایشگاه"),
            Station("l2_b17", 36.319000, 59.490500, "line_elahieh1_sabzeh", 16, "backward", "ایستگاه ۱۷ (برگشت): بلوار صادقیه"),
            Station("l2_b18", 36.317700, 59.488900, "line_elahieh1_sabzeh", 17, "backward", "ایستگاه ۱۸ (برگشت): تقاطع رحمانی"),
            Station("l2_b19", 36.316400, 59.487200, "line_elahieh1_sabzeh", 18, "backward", "ایستگاه ۱۹ (برگشت): میدان محمدیه"),
            Station("l2_b20", 36.315100, 59.485600, "line_elahieh1_sabzeh", 19, "backward", "ایستگاه ۲۰ (برگشت): بلوار اقدسیه ۱۰"),
            Station("l2_b21", 36.313800, 59.483900, "line_elahieh1_sabzeh", 20, "backward", "ایستگاه ۲۱ (برگشت): بلوار الهیه - نبش سجادیه"),
            Station("l2_b22", 36.312500, 59.482100, "line_elahieh1_sabzeh", 21, "backward", "ایستگاه ۲۲ (برگشت): مقصد - الهیه فاز یک")
        ),
        polyline = listOf(
            GeoPoint(36.312500, 59.482100),
            GeoPoint(36.313800, 59.483900),
            GeoPoint(36.315100, 59.485600),
            GeoPoint(36.316400, 59.487200),
            GeoPoint(36.317700, 59.488900),
            GeoPoint(36.319000, 59.490500),
            GeoPoint(36.320300, 59.492100),
            GeoPoint(36.321600, 59.493800),
            GeoPoint(36.322900, 59.495400),
            GeoPoint(36.324200, 59.497100),
            GeoPoint(36.325500, 59.498700),
            GeoPoint(36.326800, 59.500400),
            GeoPoint(36.328100, 59.502000),
            GeoPoint(36.329400, 59.503700),
            GeoPoint(36.330700, 59.505300),
            GeoPoint(36.332000, 59.507000),
            GeoPoint(36.333300, 59.508600),
            GeoPoint(36.334600, 59.510300),
            GeoPoint(36.335900, 59.511900),
            GeoPoint(36.337200, 59.513600),
            GeoPoint(36.338500, 59.515200),
            GeoPoint(36.339800, 59.516900)
        )
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
