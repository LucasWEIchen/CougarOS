#!/usr/bin/env python3

from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))
import evaluate_central_brain_smoking_confidence as confidence


def logprob_item(token: str, alternatives: list[tuple[str, float]]) -> dict:
    return {
        "token": token,
        "logprob": alternatives[0][1],
        "top_logprobs": [
            {"token": candidate, "logprob": logprob}
            for candidate, logprob in alternatives
        ],
    }


class SmokingConfidenceEvaluationTest(unittest.TestCase):
    def test_model_profiles_are_fixed_and_isolated(self) -> None:
        baseline = confidence.resolve_model_profile("qwen35-9b-awq")
        candidate = confidence.resolve_model_profile("qwen35-2b-awq")

        self.assertEqual("Qwen3.5-9B-AWQ", baseline["model"])
        self.assertEqual("http://127.0.0.1:10030", baseline["endpoint"])
        self.assertEqual("Qwen3.5-2B-AWQ", candidate["model"])
        self.assertEqual("http://127.0.0.1:10031", candidate["endpoint"])
        with self.assertRaisesRegex(ValueError, "fixed TY1100"):
            confidence.resolve_model_profile(
                "qwen35-2b-awq", "http://127.0.0.1:10030"
            )
        with self.assertRaisesRegex(ValueError, "fixed TY1100"):
            confidence.resolve_model_profile(
                "qwen35-2b-awq", "http://192.168.250.100:10031"
            )

    def test_candidate_request_body_uses_exact_model_identity(self) -> None:
        body = confidence.fast.request_body(
            b"jpeg",
            "image/jpeg",
            "system",
            "user",
            "schema",
            confidence.fast.compact_schema(),
            confidence.fast.FAST_MAX_TOKENS,
            "Qwen3.5-2B-AWQ",
            stop_on_closing_bracket=True,
        )

        payload = confidence.baseline.strict_json(body)
        self.assertEqual("Qwen3.5-2B-AWQ", payload["model"])
        self.assertFalse(payload["chat_template_kwargs"]["enable_thinking"])
        self.assertEqual(["]"], payload["stop"])
        self.assertTrue(payload["include_stop_str_in_output"])

    def test_fallback_request_does_not_use_compact_stop_extension(self) -> None:
        body = confidence.fast.request_body(
            b"jpeg",
            "image/jpeg",
            "system",
            "user",
            "schema",
            {"type": "object"},
            confidence.fast.FALLBACK_MAX_TOKENS,
            "Qwen3.5-2B-AWQ",
        )

        payload = confidence.baseline.strict_json(body)
        self.assertNotIn("stop", payload)
        self.assertNotIn("include_stop_str_in_output", payload)

    def test_classification_metrics_accept_empty_split(self) -> None:
        metrics = confidence.classification_metrics([])

        self.assertEqual(0, metrics["samples"])
        self.assertEqual(0, metrics["strict_valid_outputs"])
        self.assertIsNone(metrics["coverage"])
        self.assertIsNone(metrics["all_sample_accuracy_abstain_incorrect"])

    def test_compact_schema_encodes_status_field_invariants(self) -> None:
        schema = confidence.fast.compact_schema()

        self.assertEqual(3, len(schema["oneOf"]))
        self.assertEqual(0, schema["oneOf"][0]["prefixItems"][0]["const"])
        self.assertEqual(0, schema["oneOf"][0]["prefixItems"][2]["const"])
        self.assertEqual(1, schema["oneOf"][1]["prefixItems"][0]["const"])
        self.assertEqual(1, schema["oneOf"][1]["prefixItems"][1]["minimum"])
        self.assertEqual(2, schema["oneOf"][2]["prefixItems"][0]["const"])
        self.assertEqual(49, schema["oneOf"][2]["prefixItems"][3]["maximum"])

    def test_status_probabilities_extract_first_semantic_token(self) -> None:
        content = "[1, 1, 2, 95]"
        items = [
            logprob_item("[", [("[", 0.0)]),
            logprob_item("1", [("1", -0.1), ("0", -2.1), ("2", -3.1)]),
            logprob_item(", 1, 2, 95]", [("x", 0.0)]),
            logprob_item("<|im_end|>", [("<|im_end|>", 0.0)]),
        ]

        probabilities, margin = confidence.status_probabilities(content, items, 1)

        self.assertAlmostEqual(1.0, sum(probabilities.values()))
        self.assertGreater(probabilities[1], probabilities[0])
        self.assertEqual(2.0, margin)

    def test_status_probabilities_reject_unknown_hidden_suffix(self) -> None:
        items = [
            logprob_item("[", [("[", 0.0)]),
            logprob_item("1", [("1", -0.1), ("0", -2.1), ("2", -3.1)]),
            logprob_item("]", [("]", 0.0)]),
            logprob_item("<|unexpected|>", [("<|unexpected|>", 0.0)]),
        ]
        with self.assertRaisesRegex(ValueError, "do not reconstruct"):
            confidence.status_probabilities("[1]", items, 1)

    def test_status_probabilities_reject_missing_class(self) -> None:
        items = [
            logprob_item("[", [("[", 0.0)]),
            logprob_item("1", [("1", -0.1), ("0", -2.1)]),
            logprob_item("]", [("]", 0.0)]),
        ]
        with self.assertRaisesRegex(ValueError, "all status alternatives"):
            confidence.status_probabilities("[1]", items, 1)

    def test_calibrator_fits_and_predicts_bounded_probability(self) -> None:
        rows = []
        for index in range(40):
            correct = int(index % 5 != 0)
            rows.append(
                {
                    "split": "calibration",
                    "abstained": False,
                    "decision_correct": correct,
                    "self_confidence": 0.9 if correct else 0.6,
                    "selected_status_logprob_margin": 4.0 if correct else 0.2,
                    "status": 1 if index % 2 else 0,
                }
            )

        calibrator = confidence.fit_calibrator(rows)
        probability = confidence.predict_calibrated(rows[0], calibrator)

        self.assertGreater(probability, 0.0)
        self.assertLess(probability, 1.0)
        self.assertTrue(calibrator["iterations"] > 0)

    def test_calibrator_refuses_single_outcome(self) -> None:
        rows = [
            {
                "split": "calibration",
                "abstained": False,
                "decision_correct": 1,
                "self_confidence": 0.95,
                "selected_status_logprob_margin": 2.0,
                "status": 1,
            }
            for _ in range(30)
        ]
        with self.assertRaisesRegex(ValueError, "both outcomes"):
            confidence.fit_calibrator(rows)


if __name__ == "__main__":
    unittest.main()
