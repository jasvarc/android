package sk.sipky.darts.game

/**
 * Všetky ladiace konštanty hry na jednom mieste.
 *
 * Dĺžky sú v milimetroch v mierke terča (terč má do drôtu double 170 mm), časy v sekundách.
 */
object GameConfig {

    // --- Trasenie ruky ---

    /** Maximálna výchylka trasenia na každej osi (mm). Väčšia hodnota = ťažšia hra. */
    val swayAmplitudeMm = 35f

    /** Frekvencie dvoch sínusoviek pre os x (Hz). Navzájom nesúmerné, aby sa pohyb neopakoval. */
    val swayFreqX = floatArrayOf(0.8f, 1.9f)

    /** Frekvencie dvoch sínusoviek pre os y (Hz). */
    val swayFreqY = floatArrayOf(1.1f, 2.3f)

    /** Váhy pomalej a rýchlej zložky trasenia (normalizujú sa na súčet 1). */
    val swayWeights = floatArrayOf(0.6f, 0.4f)

    // --- Ovládanie ---

    /** Koľko pixelov terča sa posunie zameriavač pri posune prsta o 1 px. */
    val dragSensitivity = 1.0f

    /** Maximálny posun zameriavača ťahaním od stredu terča (mm). */
    val maxDragOffsetMm = 300f

    // --- Let a animácie ---

    /** Dĺžka letu šípky (s). */
    val flightSeconds = 0.45f

    /** Výška balistického oblúka ako podiel výšky hracej plochy. */
    val arcHeightFraction = 0.12f

    /** Ako dlho trvá návrat ruky s novou šípkou (s). */
    val handReturnSeconds = 0.3f

    /** Ako dlho je zobrazený súhrn kola (s). */
    val roundSummarySeconds = 1.2f

    /** Počet šípok v jednom kole. */
    val dartsPerRound = 3

    // --- Vzhľad ---

    /** Priemer kruhu zameriavača (mm v mierke terča). */
    val crosshairDiameterMm = 14f

    /** Akú časť trasenia zameriavača zopakuje ruka na obrazovke. */
    val handSwayFactor = 0.5f

    /** Akú časť posunu ťahaním zopakuje ruka na obrazovke. */
    val handDragFactor = 0.15f

    /** Maximálny náhodný náklon zapichnutej šípky (stupne). */
    val stuckDartMaxTiltDeg = 18f
}
