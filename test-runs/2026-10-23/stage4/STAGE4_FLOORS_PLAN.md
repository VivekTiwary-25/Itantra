# Stage 4 - floors layout (replaces spots A/B/C in agentB\stage4_plan.md; everything else in that plan still applies)

Roles: S = RMX5108 (sender, Utkarsh, LOWER floor). M = RMX3392 (middle, Vivek's room with the PC, MIDDLE floor).
R = I2221 (receiver, Suyash, UPPER floor). Spare = RMX5070 (Yash, Vivek's room, Emergency OFF the whole time).
Recipient on RMX5108 = "vimalesh". Marker word KESTREL41. Owners: Agent A (Opus) = S + R; Agent B (Sonnet) = M + spare.
Final PASS/FAIL (relay proof and middle-phone privacy) = Opus.

## How each thing is proved
1. S and R cannot see each other: S and R start first with M OFF and run 3 min. PROOF = (a) both Home screens show
   no "nearby" number (helpers read it out); (b) logs pulled afterwards: no peer_discovered / link_ready on S or R
   between the stage start and M's START. A peer seen on S or R in that window can only be the other outer phone
   (M and the spare are off). After M starts: each outer phone must show exactly "1 nearby"; "2 nearby" = they see
   each other.
2. M hears both: after M's START, M's own log (live over adb at the PC) must show link_ready to TWO different peers,
   at times that match a link_ready on S and on R; S and R each show "1 nearby". Hop proof = M logs
   dtn_bundle_rx RELAY PERSISTED then dtn_bundle_tx DESTINATION for each KESTREL41 message (sender "Relayed" alone is
   not proof).
3. M never shows or speaks the text: 0 KESTREL41 hits in M's logcat, events, DB (+wal); 0 product_delivered /
   product_tts_start on M; M's contacts/delivered/outgoing stay 0 rows (RELAY bundles only).

## If M cannot reach a floor (M shows only 1 link after 90 s, or a helper reads no "nearby" number)
1. First Vivek moves RMX3392 to his room's door / the stairwell side of his floor, waits 90 s.
2. Still not reached: ONLY the helper on the unreached floor carries their phone to the stairwell end of THEIR floor
   (not onto the stairs), lays it down, waits 90 s.
3. After any move both helpers read their number again: must be exactly 1 each. If either shows 2, the outer phones
   see each other through the stairwell - move that phone back from the stairs.
4. Never move both outer phones toward the stairwell at the same time. Spare RMX5070 is NOT used as a second middle
   (that would be a different test) unless Vivek decides.

## If wireless adb does not reach the upper/lower floor
Nothing is lost: logs and event files stay on the phone. Agents record every phone's clock before the phones leave
the PC; after the end the phones come back to the PC STILL RUNNING and the logs are pulled before anyone taps STOP.
The helpers write down the time they start walking back; analysis stops at that time.
