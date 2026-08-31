import unittest

from minipaintdex_data.official_refresh import _usage, parse_prince_august_cards, parse_vallejo_lines


class OfficialRefreshTest(unittest.TestCase):
    def test_parses_prince_august_product_card(self):
        page = """
        <div class="single-element"><img src="https://example.test/P830.jpg">
        <h3><a href="https://www.prince-august.net/peintures/classic/102-vert">P830 &#8211; 102 &#8211; Vert Allemand WWII</a></h3>
        <p>Peinture acrylique mate.</p></div>
        """

        self.assertEqual(
            parse_prince_august_cards(page),
            [{
                "reference": "P830",
                "name": "Vert Allemand WWII",
                "url": "https://www.prince-august.net/peintures/classic/102-vert",
                "image": "https://example.test/P830.jpg",
                "description": "Peinture acrylique mate.",
            }],
        )

    def test_parses_vallejo_reference_and_english_name(self):
        lines = ["Game Color chart", "72. 401", "Templar White", "Blanco Templario", "72.402", "Dwarf Skin"]

        self.assertEqual(
            parse_vallejo_lines(lines, prefix="72."),
            [("72.401", "Templar White"), ("72.402", "Dwarf Skin")],
        )

    def test_ignores_repeated_vallejo_references_in_safety_notices(self):
        lines = ["70.790", "Silver", "Plata", "70.790", "Danger. Flammable liquid."]

        self.assertEqual(
            parse_vallejo_lines(lines, prefix="70."),
            [("70.790", "Silver")],
        )

    def test_generic_technical_guidance_is_explicitly_marked_for_review(self):
        usage = _usage("technical_effect", "Mud Effect")

        self.assertEqual(usage["instruction_status"], "generic_template")
        self.assertTrue(usage["review_required"])


if __name__ == "__main__":
    unittest.main()
