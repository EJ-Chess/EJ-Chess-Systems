import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'bg-green-600/20 text-green-400 ring-1 ring-green-600/30',
        secondary: 'bg-zinc-700 text-zinc-300',
        destructive: 'bg-red-900/30 text-red-400 ring-1 ring-red-700/30',
        warning: 'bg-yellow-900/30 text-yellow-400 ring-1 ring-yellow-700/30',
        outline: 'border border-zinc-600 text-zinc-300',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
