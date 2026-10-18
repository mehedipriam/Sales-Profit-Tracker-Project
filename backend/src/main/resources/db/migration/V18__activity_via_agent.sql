-- Orders created through the order assistant (the chat box) are marked in the activity log, so the team can tell them
-- apart from orders typed into the order form.
ALTER TABLE order_activity ADD COLUMN via_agent BOOLEAN NOT NULL DEFAULT FALSE;
