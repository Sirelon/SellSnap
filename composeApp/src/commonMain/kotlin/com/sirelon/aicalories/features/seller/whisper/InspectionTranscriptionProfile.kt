package com.sirelon.sellsnap.features.seller.whisper

object InspectionTranscriptionProfile {
    const val model: String = "gpt-4o-transcribe"

    const val prompt: String =
        "The speaker is a construction or building inspector dictating field notes. " +
                "Expect construction terminology, building defects, safety observations, " +
                "measurements, locations, materials, and trade-specific abbreviations. " +
                "Common terms include concrete, rebar, formwork, slab, beam, column, " +
                "load-bearing wall, deflection, spalling, cracking, delamination, " +
                "efflorescence, waterproofing, membrane, flashing, sealant, firestopping, " +
                "drywall, framing, insulation, HVAC, MEP, electrical panel, conduit, " +
                "junction box, plumbing riser, drain, expansion joint, anchor bolt, " +
                "fastener, corrosion, moisture intrusion, code violation, nonconformance, " +
                "punch list, RFI, NCR, change order, as-built, tolerance, grade, slope, " +
                "plumb, level, clearance, egress, handrail, guardrail, stair tread, " +
                "landing, occupancy, and life safety. Preserve measurements, room names, " +
                "floor numbers, grid lines, equipment tags, brand names, contractor names, " +
                "and abbreviations exactly when spoken. Do not translate; keep the " +
                "speaker's original language."
}
