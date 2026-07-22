BEGIN;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cancel_reason_check;

ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (
                                                                        'RESERVING',
                                                                        'PENDING_CHARGE',
                                                                        'PENDING_AUTHORIZATION',
                                                                        'AUTHORIZED',
                                                                        'PENDING_CAPTURE',
                                                                        'PENDING_VOID',
                                                                        'CONFIRMED',
                                                                        'CANCELLED'
    ));

ALTER TABLE orders ADD CONSTRAINT orders_cancel_reason_check CHECK (cancel_reason IN (
                                                                                      'INSUFFICIENT_STOCK',
                                                                                      'INVENTORY_UNREACHABLE',
                                                                                      'RESERVATION_INCOMPLETE',
                                                                                      'PAYMENT_DECLINED',
                                                                                      'PAYMENT_TIMEOUT',
                                                                                      'DEAL_FAILED',
                                                                                      'PARTICIPANT_LEFT'
    ));

COMMIT;