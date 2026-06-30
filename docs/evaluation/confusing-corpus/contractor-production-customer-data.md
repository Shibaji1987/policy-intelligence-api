# Contractor Production Customer Data Access Standard

## Purpose

This standard controls contractor access to production customer data during support, incident response, and emergency remediation.

## Policy

Contractors may access production customer data only when a named data owner records explicit approval before access begins. The approval record must include the contractor identity, support ticket, production system, customer data category, access reason, and expiry time.

Emergency contractor access is allowed only for active production incidents. Emergency access must expire automatically within four hours unless a data owner renews it in writing. The access session must create immutable audit evidence, including command history, query history, and a reviewer sign-off.

Contractors must not use standing production credentials. Contractors must not copy production customer data into collaboration tools, local devices, or non-production environments.

## Evidence

Required evidence includes data owner approval, support ticket, time-bound access grant, audit log, and post-incident review note.

