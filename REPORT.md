# Dependable Distributed Systems Project 2 (Trust-Aware Tor Path Selection Prototype)

---

## Overview

This report documents the projects' implementation of a simplified version of the TrustAll variant of the Trust-Aware Path Selection (TAPS) algorithm, focused around the data-sharing dynamics between country-level alliances, in order to generate safer Tor circuits which minimize surveillance around guard and exit relays.
It assumes direct inter-country links and abstracts away the complexities of real-world Internet routing. The goal is to select paths that optimize both anonymity and performance using preprocessed Tor relay data and a configurable trust model.

## 1. guardSecurity(String clientIP, List<Relay> guards)

**Purpose:**  
Calculates a trust score for each potential guard relay, based on the client's location and a user-defined trust model involving country alliances.

**Key Steps:**
1. Determine the origin country of the provided client and guard relay IPs.
2. Retrieves all allied countries for both client and guard countries.
3. Computes the trust score of the guard's country.
4. If there is no intersection between the client's allies and the guard's allies, the guard's trust is penalized (multiplied by a constant, default: 0.75).
5. Returns a map of guard → trust score.

**Rationale:**  
Step 4's penalty is a heuristic meant to discourage selecting guards whose alliances have no direct connection to the client country's alliance, in order to at least simulate some possible surveillance in the unknown path between countries/alliances.

---

## 2. exitSecurity(String clientIP, Relay guard, Relay exit, String destinationIP)

**Purpose:**  
Evaluates the security of an entry → exit pair by detecting potential adversaries that can observe both ends of the Tor circuit.

**Key Steps:**
1. Resolves country codes for client, guard, exit, and destination IPs.
2. Identifies adversarial country sets that can observe the entry side (client + guard) and the exit side (exit + destination).
3. Computes the intersection of both adversary sets.
4. If the sets intersect, then the lowest trust value among shared adversaries is returned (representing the weakest link).
5. Otherwise, if no adversary is common to both sides, the circuit is considered maximally safe, and the method returns a score of 1.0.

**Rationale:**  
Prevent any single adversary from observing both ends of a circuit. A lower trust score implies greater risk, and thus a lower chance of being picked in the next steps.

---

## 3. selectPath(String clientIP, String destinationIP, AlphaParams guardParams, AlphaParams exitParams)

**Purpose:**  
Computes a standard 3-hop Tor path (guard → middle → exit), using the available Tor relays information, and respecting the provided adversarial model and alpha parameters.

**Key Steps:**

1. Uses `rankGuardRelays` to generate a list of best guard relay candidates, using `guardSecurity` to calculate trust scores for every guard, filtering them into safe or acceptable "buckets" via the provided alpha thresholds, shuffling them using a bandwidth-weighted approach, and finally, joins the 2 shuffled buckets in descending order (for possible retries).
2. Iterates through each guard in the aforementioned descending/shuffled list.
   1. For the current guard iteration, `rankExitRelays()` filters out relays that lack exit capabilities, or that are in the same family as the guard, then, computes trusted exit relays via `exitSecurity`, and follows the same safe/acceptable bucket bandwidth-weighted shuffle process as the guards.
      1. For the current (guard, exit) pair iteration, the method checks for eligible middle relays not in the same family as either end. If valid middle relays are found, one is selected using a single bandwidth-weighted call, and the [guard, middle, exit] path is returned.
      2. Otherwise, if there are no valid middle candidates, the algorithm reverts to step i., and retries with the next best exit.
   2. Otherwise, if there are no valid exit candidates, the algorithm reverts to step 2., and retries with the next best guard.
3. If no valid path is found across all retries, the method throws a PathSelectionException.

**Rationale:**  
The algorithm is robust and tries every possible combination by incorporating retry logic across each layer of the path. If a valid exit cannot be found for a guard, the algorithm backtracks and tries the next guard. If no valid middle exists for a (guard, exit) pair, it tries the next exit, accordingly.  
After applying the alpha thresholds to separate relays into safe and acceptable buckets, the guard/exit relays are randomly ordered with a probability proportional to their measured bandwidth. This provides a good balance between trust and performance, by preferring higher-capacity relays without making the decision fully deterministic (better bandwidth utilization distribution).
